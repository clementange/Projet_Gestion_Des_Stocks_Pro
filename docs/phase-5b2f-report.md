# Phase 5b-2f — un create ne peut jamais être un update — Rapport

Vecteur générique : tous les `toEntity` des DTO applicatifs propagent `dto.getId()`, et
`repository.save()` sur une entité portant un id existant est un **MERGE JPA**. Tout endpoint de
création qui laisse passer un id client est donc un endpoint de mise à jour déguisé, exploitable
d'un tenant à l'autre.

Périmètre corrigé : `/sites/create`, `/users/create`, `/user-role-assignments/create` (+
`organizationId` forcé depuis le principal), et **suppression** de `/roles/create` et
`/permissions/create`. Les 10 autres endpoints vulnérables sont audités, inventoriés et
délibérément non touchés.

## 1. Reproduction — rouge d'abord, et pour la bonne raison

### 1.1 `/sites/create`

Rouge : `AssertionError: Status expected:<400> but was:<200>`. Un probe temporaire (exécuté puis
retiré, non commité) a documenté l'impact, qui dépasse l'écrasement :

| Requête | Résultat observé |
|---|---|
| B poste avec l'id du site de A + sa propre ville | **200 OK** — site réécrit, repointé sur la ville de B donc sur l'organisation de B |
| A relit son propre site | **404 `SITE_NOT_FOUND`** — A est évincé |
| B relit ce même site | **200 OK** — B en est propriétaire |

**Appropriation complète**, pas un simple écrasement : c'est la lecture org-scopée (5b-2b/2c) qui
rend le vol total plutôt que partiel.

### 1.2 `/users/create` et `/user-role-assignments/create`

Trois tests, rouges puis verts. Deux itérations ont été nécessaires pour obtenir un rouge
**honnête**, ce qui vaut d'être noté :

- Premier run : `createAssignmentMustNotOverwrite...` échouait **dans son setup**
  (`expected:<200> but was:<400>`) — `UserRoleAssignmentValidator` exige `organizationId` depuis
  5b-1, que ma fixture omettait. Rouge de fixture, pas de production.
- Deuxième run : l'attaque elle-même était rejetée en `USER_ROLE_ASSIGNMENT_NOT_VALID` — **pour la
  mauvaise raison**, car le payload d'attaque omettait lui aussi `organizationId`. Un vrai attaquant
  le fournirait. Corrigé pour que l'attaque soit réaliste.
- Troisième run — baseline propre, les trois en `expected:<4xx> but was:<200>` :

| Test | Rouge |
|---|---|
| `createUserMustNotOverwriteAnExistingUserViaClientSuppliedId` | 400 attendu, **200** obtenu |
| `createAssignmentMustNotOverwriteAnExistingAssignmentOfAnotherTenantViaClientSuppliedId` | 400 attendu, **200** obtenu |
| `createAssignmentMustIgnoreClientSuppliedOrganizationId` | 404 attendu, **200** obtenu — A lisait l'affectation que B avait ancrée dans l'organisation de A |

Le test `/users/create` prouve explicitement ce qui était demandé : la victime se reconnecte avec
son email **et** son mot de passe d'origine après l'attaque ; l'email choisi par l'attaquant n'est
jamais devenu un identifiant valide ; et le mot de passe de l'attaquant ne fonctionne pas sur le
compte de la victime.

## 2. Mécanisme central (décision 1)

`validator/CreationIdGuard.requireNoId(Long id, String entityLabel)` — point d'entrée unique, hors
module (même emplacement partagé qu'`AdresseValidator`, déjà importé par les validateurs de
plusieurs modules). Lève `InvalidEntityException` avec le **code dédié**
`ErrorCodes.CREATION_ID_FORBIDDEN` (32000) → HTTP 400.

Rejet explicite plutôt que mise à `null` silencieuse : aucun endpoint d'update n'existe pour Site
ou User, donc un appelant qui croyait mettre à jour ne doit pas repartir convaincu d'avoir réussi
alors qu'une ressource neuve a été créée à côté.

Appelé **en premier**, avant la validation de forme : un id fourni est une erreur de protocole sans
ambiguïté, et les garde-fous d'appartenance existants (City en 5b-2d, Site en 5b-2b) ne protègent
pas de ce vecteur — ils valident la référence *fournie*, jamais l'entité *ciblée* par l'id.
`SiteServiceImpl` a été migré de sa méthode privée (premier commit de l'incrément) vers ce
mécanisme ; son test a suivi, de `SITE_NOT_VALID` vers `CREATION_ID_FORBIDDEN` — changement de
contrat assumé dans le même incrément, l'invariant étant transverse et non propre à Site.

### Le test d'inventaire (décision 1, seconde partie)

`architecture/CreateEndpointIdGuardTest`, en deux parties :

1. **Dérive** — énumère les `POST .../create` réellement exposés depuis le
   `RequestMappingHandlerMapping` et exige une correspondance exacte avec l'inventaire déclaré
   (`GUARDED` + `KNOWN_UNGUARDED_DEBT`). Un endpoint neuf non classé fait échouer le test, forçant
   une décision explicite.
2. **Application** — vérifie **sur le bytecode** (ArchUnit `ClassFileImporter`) que chaque service
   déclaré protégé appelle réellement `CreationIdGuard.requireNoId`. Retirer le garde-fou casse le
   test.

L'approche runtime a immédiatement payé : elle a révélé `/utilisateurs/create` →
`UtilisateurController#save`, que les greps sur `*Controller.java` manquaient (route legacy déclarée
sur une interface `*Api`). Elle est **déjà protégée**, car elle délègue à `UserServiceImpl.save` —
et son `toUserDto` propage lui aussi `dto.getId()`, donc la faille y existait à l'identique.

**Les deux parties ont été vérifiées par mutation** : déclarer `CategoryServiceImpl` comme protégé
fait échouer la partie 2 ; ajouter un `/ghost/create` fantôme à l'inventaire fait échouer la
partie 1. Le test n'est pas vert à vide.

**Limite assumée** : la partie 1 détecte la *dérive d'inventaire*, elle ne prouve pas l'absence de
faille. Le prouver exige un payload valide propre à chaque endpoint — ce que font les tests
d'intégration dédiés. Les deux niveaux sont complémentaires, et le rapport le dit plutôt que de
laisser croire à une preuve généralisée.

## 3. Suppression de `/roles/create` et `/permissions/create` (décision 3)

`Role` et `Permission` sont un **catalogue global** sans `organizationId` (confirmé en 5b-2c), seedé
par Flyway (V3/V4). `RBAC_MANAGE` ne protégeait rien : tout admin de tenant le possède via le
bootstrap `ADMINISTRATEUR`. N'importe lequel pouvait donc créer — et, via un id fourni, **écraser**
— une entrée utilisée par *tous* les tenants (y compris le rôle `ADMINISTRATEUR` lui-même). Même
précédent que `/organizations/create` en 5b-2e.

Appelants vérifiés avant suppression : en production, `roleService.save`/`permissionService.save`
n'étaient appelés **que** par les deux contrôleurs supprimés ; `TenantRegistrationServiceImpl`
utilise `roleService.findByCode("ADMINISTRATEUR")`, pas `save`. Les méthodes de service sont donc
**conservées** — une douzaine de suites de tests s'en servent pour leur seeding RBAC, et un futur
amorçage en aurait besoin.

`RbacAdminEndpointsHttpIntegrationTest` a été retravaillé pour préserver l'intention de couverture
sans les routes disparues, sans rien affaiblir :

| Test | Avant | Après |
|---|---|---|
| `entrepriseAdminCanCreatePermissionsAndRoles` | POST create ×2 | renommé `...CanReadPermissionsAndRoles`, vérifie le franchissement du gate sur `GET /permissions/all` et `/roles/all` |
| `secondEntrepriseAdminIsAlsoAutomaticallyGrantedRbacManage` | POST create | même garantie via `GET /permissions/all` (route `RBAC_MANAGE` survivante) |
| `userWithNoRoleAssignmentCannotSelfGrantARole` | POST create refusé | `GET /permissions/all` refusé (`PERMISSION_ACCESS_DENIED`), partie auto-attribution inchangée |
| `plainUserCannotReadRolesOrPermissions` | seeding par HTTP | seeding par `PermissionService`/`RoleService`, assertions de gating identiques |

## 4. Inventaire complet des endpoints de création

15 `POST .../create` exposés (relevé runtime). Les DTO de *ligne* (`SaleLineDto`,
`CustomerOrderLineDto`, `PurchaseOrderLineDto`, `StockTransferLineDto`) sont les seuls à ne pas
propager d'id : ils exposent `toNewEntity` — le bon motif, déjà présent.

| Endpoint | État |
|---|---|
| `/sites/create` | **Protégé** (5b-2f) |
| `/users/create` | **Protégé** (5b-2f) |
| `/utilisateurs/create` (legacy) | **Protégé** (5b-2f, par délégation à `UserServiceImpl`) |
| `/user-role-assignments/create` | **Protégé** (5b-2f) + `organizationId` forcé |
| `/roles/create` | **Supprimé** (5b-2f) |
| `/permissions/create` | **Supprimé** (5b-2f) |
| `/organizations/create` | Supprimé en 5b-2e |
| `/articles/create` | Dette inventoriée |
| `/categories/create` | Dette inventoriée |
| `/cities/create` | Dette inventoriée — aucune notion d'organisation, `organization.id` libre |
| `/warehouses/create` | Dette inventoriée — aucune notion d'organisation |
| `/customers/create` | Dette inventoriée — **voir §5** |
| `/suppliers/create` | Dette inventoriée — **voir §5** |
| `/sales/create` | Dette inventoriée |
| `/customer-orders/create` | Dette inventoriée |
| `/purchase-orders/create` | Dette inventoriée |
| `/stock-transfers/create` | Dette inventoriée |
| `/tenants/register` | Non concerné — passe par `TenantRegistrationService`, pas `TenantService.save` |

## 5. Customer / Supplier : réponse à la question posée (décision 4)

**Non, il n'existe aucun endpoint d'update séparé.** Les routes exposées sont exactement :

```
/customers/all   /customers/create   /customers/{id}   /customers/delete/{id}
/suppliers/all   /suppliers/create   /suppliers/{id}   /suppliers/delete/{id}
```

Conséquence directe pour la suite : `save()` y est **délibérément un upsert** (documenté en Phase
5a — le pré-check d'unicité de mail s'exclut explicitement lui-même, `!existing.getId().equals(dto.getId())`,
précisément pour permettre la mise à jour d'un client existant). Appliquer `CreationIdGuard` à ces
deux endpoints supprimerait donc **le seul moyen HTTP de modifier un client ou un fournisseur**.

Ce n'est plus un correctif de sécurité isolé mais une décision fonctionnelle : il faudrait d'abord
introduire un `PUT`/`PATCH` dédié, ou scinder `save()` en `create()` + `update()`. C'est exactement
la frontière create/update évoquée en §6 du rapport précédent, et la raison pour laquelle ces deux
endpoints ne sont pas dans le périmètre.

## 6. Critère de sortie

| Critère | Résultat |
|---|---|
| Test rouge montré avant correctif, pour chaque endpoint du périmètre | **Vérifié** — 4 rouges (Site + 3 identity), dont 2 itérations pour écarter un rouge de mauvaise raison |
| `/users/create` : email ET mot de passe de la victime inchangés | **Vérifié** — reconnexion d'origine OK, email et mot de passe de l'attaquant inopérants |
| Mécanisme central, pas 14 copies | **Vérifié** — `CreationIdGuard`, code dédié `CREATION_ID_FORBIDDEN` |
| Test énumérant les `POST .../create` et échouant sur dérive | **Vérifié** — 2 parties, **toutes deux validées par mutation** |
| `organizationId` ne vient plus du client (affectations) | **Vérifié** — forcé depuis le principal, toujours écrasé |
| `/roles/create` et `/permissions/create` supprimés, services conservés, appelants vérifiés | **Vérifié** |
| Customer/Supplier et autres entités non touchés | **Vérifié** |
| Aucun `V*.sql` modifié, aucun `@Disabled` | **Vérifié** |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** |
| Tous les tests verts | **Vérifié** — 244 tests, 0 échec, 0 erreur (238 avant l'incrément) |

## 7. Fichiers modifiés

**Production** : `validator/CreationIdGuard.java` (nouveau), `exception/ErrorCodes.java`
(+`CREATION_ID_FORBIDDEN`), `organization/application/impl/SiteServiceImpl.java`,
`identity/application/impl/UserServiceImpl.java`,
`identity/application/impl/UserRoleAssignmentServiceImpl.java`,
`identity/presentation/rest/UserRoleAssignmentController.java`,
`identity/presentation/rest/RoleController.java`,
`identity/presentation/rest/PermissionController.java`.

**Test** : `identity/CreateEndpointIdInjectionHttpIntegrationTest.java` (nouveau, 3 tests),
`architecture/CreateEndpointIdGuardTest.java` (nouveau, 2 tests),
`organization/OrganizationControllersHttpIntegrationTest.java`,
`identity/RbacAdminEndpointsHttpIntegrationTest.java`.

**Docs** : `docs/migration-notes.md`.

Builds sous JDK 21 (`JAVA_HOME=~/.jdks/ms-21.0.10`) : Lombok 1.18.30 reste incompatible avec le
JDK 25 de l'environnement — écart documenté en 5b-2e §4, `pom.xml` non touché.

## 8. Prochaine étape

**Arrêt.** 10 endpoints restent vulnérables au même vecteur, tous inventoriés et verrouillés contre
la dérive par `CreateEndpointIdGuardTest`. Deux groupes à traiter différemment :

- **Sans endpoint d'update** (`cities`, `warehouses`, `sales`, `customer-orders`,
  `purchase-orders`, `stock-transfers`, `articles`, `categories`) : `CreationIdGuard` s'applique
  directement, incrément mécanique.
- **Avec upsert assumé** (`customers`, `suppliers`) : nécessite d'abord un endpoint d'update dédié
  — décision fonctionnelle, voir §5.

Backlog antérieur inchangé : RBAC-on-create (« qui a le droit de créer »), et montée de Lombok pour
JDK 25.
