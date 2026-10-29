# Phase 5b-2f — écriture inter-tenant via id client sur les endpoints create — Rapport

Incrément en trois étapes imposées : reproduire d'abord (test rouge), corriger `Site` seulement,
puis auditer l'ensemble des endpoints `create` et **attendre un feu vert** avant d'élargir.
Seul `Site` est corrigé dans ce commit.

## 1. Reproduction (étape 1) — test rouge, puis preuve de l'impact réel

`SiteDto.toEntity` fait `site.setId(dto.getId())` ; `SiteValidator` ne rejette pas un id fourni ;
`siteRepository.save()` sur une entité portant un id existant est un **MERGE JPA**, donc une mise à
jour. La vérification d'appartenance de `City` posée en 5b-2d ne protège pas : elle ne regarde que
la ville fournie, jamais l'entité ciblée par l'id.

Test ajouté :
`OrganizationControllersHttpIntegrationTest.createSiteMustNotOverwriteAnExistingSiteOfAnotherTenantViaClientSuppliedId`
— tenant A crée un site ; tenant B poste sur `/sites/create` avec l'id du site de A et **sa propre**
ville. Premier run, avant correctif : `AssertionError: Status expected:<400> but was:<200>` — la
requête de B était acceptée.

Un probe temporaire (ajouté, exécuté, puis retiré — il n'est pas commité) a documenté l'impact
exact, qui dépasse le simple écrasement :

| Requête | Résultat observé |
|---|---|
| B poste avec l'id du site de A | **200 OK** — site `id=2` réécrit (`code=HIJACKED`, `type=BOUTIQUE`, `active=false`) et repointé sur la ville de B (`city.id=4`, `organization.id=2`) |
| A relit son propre site | **404 `SITE_NOT_FOUND`** — A a perdu l'accès à son propre site |
| B relit ce même site | **200 OK** — B en est désormais propriétaire |

Ce n'est donc pas une écriture inter-tenant « en aveugle » mais une **appropriation complète** : le
site change d'organisation, le propriétaire légitime est évincé, l'attaquant récupère l'actif. La
lecture org-scopée introduite en 5b-2c/2b rend le vol total plutôt que partiel.

## 2. Correctif `Site` (étape 2)

`SiteServiceImpl.save` : nouveau `requireNoClientSuppliedId(dto)`, exécuté après la validation de
forme et **avant** `requireCityInOrganization`. Un `dto.getId()` non-null lève
`InvalidEntityException(SITE_NOT_VALID)` avec l'erreur
« L'identifiant d'un site ne peut pas etre fourni a la creation ».

**Rejet explicite plutôt que mise à `null` silencieuse**, conformément à la consigne : un appelant
qui croyait faire une mise à jour (il n'existe aucun endpoint d'update de site) ne doit pas repartir
convaincu d'avoir réussi alors qu'un site neuf vient d'être créé à côté. Le silence transformerait
une tentative d'écrasement en création fantôme difficile à diagnostiquer.

Après correctif : les 5 tests de `OrganizationControllersHttpIntegrationTest` passent — le nouveau,
et les 4 préexistants, qui prouvent que la création légitime (sans id) n'est pas affectée.

## 3. Audit exhaustif des endpoints `create` (étape 3)

Méthode, appliquée à chaque endpoint : (a) le `toEntity` du DTO propage-t-il `dto.getId()` ?
(b) le service passe-t-il cette entité directement à `repository.save()` (donc merge) ? (c) l'entité
visée par cet id peut-elle appartenir à une autre organisation ? (d) le contrôleur force-t-il
l'organisation depuis le principal — ce qui, en cas de merge, **achève** le transfert de propriété
au lieu de l'empêcher ?

Les 16 DTO applicatifs portant un `id` propagent tous `dto.getId()` dans `toEntity`. Les DTO de
*ligne* (`SaleLineDto`, `CustomerOrderLineDto`, `PurchaseOrderLineDto`, `StockTransferLineDto`) ne
le font pas : ils exposent `toNewEntity`, qui n'affecte jamais d'id — c'est le bon motif, déjà
présent dans le code.

| # | Endpoint | id client → `save()` ? | Cible peut être d'une autre org ? | Org forcée depuis le principal ? | Verdict |
|---|---|---|---|---|---|
| 1 | `POST /sites/create` | oui | oui | non (City vérifiée depuis 5b-2d) | **CORRIGÉ dans ce commit** — exploit prouvé |
| 2 | `POST /articles/create` | oui | oui | oui → achève le transfert | **Exploitable**, même forme que Site |
| 3 | `POST /categories/create` | oui | oui | oui → achève le transfert | **Exploitable**, même forme que Site |
| 4 | `POST /customers/create` | oui | oui | oui → achève le transfert | **Exploitable** (`save` déjà assumé upsert en 5a) |
| 5 | `POST /suppliers/create` | oui | oui | oui → achève le transfert | **Exploitable** (idem Customer) |
| 6 | `POST /cities/create` | oui | oui | **non, aucune** — `organization.id` vient du client | **Exploitable**, pire : l'org cible est librement choisie |
| 7 | `POST /warehouses/create` | oui | oui | **non, aucune** — seul le *type* du site est vérifié en base | **Exploitable** |
| 8 | `POST /sales/create` | oui | oui | non (Site vérifié depuis 5b-2b) | **Exploitable** — pré-check de code contourné avec un code neuf |
| 9 | `POST /customer-orders/create` | oui | oui | non (Site vérifié) | **Exploitable** (idem Sale) |
| 10 | `POST /purchase-orders/create` | oui | oui | non (Site vérifié) | **Exploitable** (idem Sale) |
| 11 | `POST /stock-transfers/create` | oui | oui | non (2 sites vérifiés) | **Exploitable**, et aucun pré-check de code |
| 12 | `POST /users/create` | oui | oui | **non, aucune** (`idEntreprise` vient du client) | **Exploitable — primitive de prise de contrôle de compte** (voir ci-dessous) |
| 13 | `POST /roles/create` | oui | **catalogue global** (pas d'`organizationId`) | s.o. | **Exploitable**, portée globale |
| 14 | `POST /permissions/create` | oui | **catalogue global** | s.o. | **Exploitable**, portée globale |
| 15 | `POST /user-role-assignments/create` | oui | oui (`organizationId` vient du client) | non | **Exploitable** |
| 16 | `POST /tenants/register` | non — passe par `TenantRegistrationService`, pas `TenantService.save` | — | — | Non atteignable par cette voie |
| — | `OrganizationService.save` | plus aucune surface HTTP depuis 5b-2e | — | — | Non atteignable |

### Points qui méritent une lecture attentive avant de trancher le périmètre suivant

- **`/users/create` (#12) est probablement le plus grave après Site.** `userAlreadyExists(email)`
  rejette tout email déjà pris et **n'exclut pas son propre id** — l'attaquant fournit donc un email
  neuf, ce qui est trivial, puis l'id de la victime : le merge réécrit la ligne utilisateur avec
  l'email et le mot de passe (encodé) choisis par l'attaquant. L'endpoint n'exige que
  `authenticated()`. Aucune organisation n'est forcée.
- **`/roles/create` et `/permissions/create` (#13, #14) ont une portée globale, pas inter-tenant.**
  `Role` et `Permission` sont un catalogue partagé sans `organizationId` (confirmé en 5b-2c).
  Écraser par id le rôle `ADMINISTRATEUR` ou une permission seedée par V3/V4 affecte **tous** les
  tenants à la fois. Les deux endpoints sont gatés par `RBAC_MANAGE`, que tout admin de tenant
  possède via le bootstrap.
- **`/cities/create` (#6) et `/warehouses/create` (#7) n'ont aucune notion d'organisation** sur la
  création : ni forçage, ni vérification. Pour City, le client fournit librement
  `organization.id` — l'attaquant choisit donc aussi l'organisation de destination.
- **#8–#11 ont déjà un garde-fou d'appartenance de site** (5b-2b/2d) qui ne protège pas de ce
  vecteur, exactement comme pour Site : le garde-fou valide le site *fourni*, jamais l'entité visée
  par l'id.
- Les pré-checks d'unicité (code pour Sale/CustomerOrder/PurchaseOrder, mail pour
  Customer/Supplier, email pour User) **ne protègent pas** : il suffit de fournir une valeur neuve.

### Correctif générique envisageable (à décider, non implémenté)

Les 14 cas exploitables partagent une cause unique : `create` et `update` passent par le même
`repository.save()`, et l'id est lu depuis le DTO. Deux pistes, à arbitrer :

1. **Rejet par endpoint** — répliquer `requireNoClientSuppliedId` dans chaque service de création.
   Explicite, localisé, testable un par un ; répétitif (14 fois).
2. **`toNewEntity` systématique** — généraliser le motif déjà utilisé par les DTO de ligne :
   un `toNewEntity` qui n'affecte jamais l'id, utilisé par les chemins de création. Supprime la
   classe de bug à la racine, mais touche plus de fichiers et demande de distinguer proprement
   création et mise à jour là où `save()` est aujourd'hui volontairement un upsert
   (`Customer`/`Supplier`, documenté en 5a ; `Tenant.updatePhoto`).

Option 1 est la plus sûre à incrément constant ; option 2 est structurellement meilleure mais
redéfinit la frontière create/update, ce qui dépasse un incrément.

## 4. Critère de sortie

| Critère | Résultat |
|---|---|
| Test rouge reproduisant l'écriture inter-tenant, montré avant correctif | **Vérifié** — `expected:<400> but was:<200>`, puis impact documenté (200 / 404 / 200) |
| `Site` : id fourni à la création rejeté explicitement | **Vérifié** — `InvalidEntityException`/`SITE_NOT_VALID` |
| Création légitime de site (sans id) inchangée | **Vérifié** — 4 tests préexistants verts |
| Audit de tous les endpoints `create` présenté | **Vérifié** — 16 lignes, §3 |
| Aucun autre correctif que `Site` | **Vérifié** — en attente de feu vert |
| Aucun `V*.sql` existant modifié, aucun `@Disabled` | **Vérifié** — aucune migration touchée |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** |
| Tous les tests verts | **Vérifié** — 239 tests, 0 échec, 0 erreur (238 avant, +1 test dédié) |

## 5. Fichiers modifiés

**Production** : `organization/application/impl/SiteServiceImpl.java` (+`requireNoClientSuppliedId`).

**Test** : `organization/OrganizationControllersHttpIntegrationTest.java` (+1 test de
non-régression, +helper `createCity`).

**Docs** : `docs/migration-notes.md`.

Builds exécutés sous JDK 21 (`JAVA_HOME=~/.jdks/ms-21.0.10`) : Lombok 1.18.30 reste incompatible
avec le JDK 25 de l'environnement — écart documenté en 5b-2e §4, toujours non corrigé, `pom.xml`
non touché.

## 6. Prochaine étape

**Arrêt demandé.** 14 endpoints restent exploitables par le même vecteur (§3). Rien n'est corrigé
au-delà de `Site` sans feu vert explicite, et le choix entre correctif par endpoint (option 1) et
`toNewEntity` systématique (option 2) reste à trancher.

Backlog antérieur inchangé : RBAC-on-create (« qui a le droit de créer », distinct de ce vecteur),
et montée de Lombok pour JDK 25.
