# Phase 5b-2h — appartenance sur delete/update + matrice inter-tenant automatique — Rapport

La matrice automatique est le livrable central, et elle a **immédiatement payé** : écrite d'abord,
elle a révélé **30 fuites inter-tenant** là où le périmètre annoncé n'en prévoyait que 7 (les
`delete`). Toutes relevaient du motif « appartenance », donc toutes ont été corrigées ici.

## 1. Investigation — deletes

| Ressource | `@AuthenticationPrincipal` sur le handler ? | Appartenance vérifiée ? |
|---|---|---|
| articles, categories, cities, sites, warehouses, customers, suppliers | **non** (7/7) | **non** |
| users, tenants, organizations, user-role-assignments | oui | users/tenants/organizations : oui (5b-2c) ; **URA : non** |
| roles, permissions | oui (`RBAC_MANAGE`) | n/a — catalogue **global** |
| `/utilisateurs/delete` (legacy) | **non** | **non** — appelait `userService.delete(id)`, variante non filtrée |

## 2. Ce que la matrice a révélé (30 fuites, toutes HTTP 200)

Première exécution, avant tout correctif. Extrait du verdict :

```
GET  /cities/{idCity}                                  -> HTTP 200
GET  /warehouses/{idWarehouse}                         -> HTTP 200
GET  /sales/{idVente}/lignes                           -> HTTP 200
GET  /customer-orders/{idCommande}/lignes              -> HTTP 200
GET  /purchase-orders/{idCommande}/lignes              -> HTTP 200
GET  /stock-transfers/{idTransfert}/lignes             -> HTTP 200
GET  /cities/filter/organization/{idOrganization}      -> HTTP 200
GET  /articles/historique/{vente,commandeclient,commandefournisseur}/{idArticle} -> HTTP 200
GET  /reporting/stock/site/{idSite}                    -> HTTP 200
POST /customer-orders/{id}/{valider,reserver,preparer,expedier,livrer}            -> HTTP 200
POST /purchase-orders/{id}/{valider,annuler}                                     -> HTTP 200
POST /stock-transfers/{id}/{demander,approuver,preparer,expedier,receptionner}   -> HTTP 200
POST /articles/{id}/photo, /clients/{id}/photo, /fournisseurs/{id}/photo         -> HTTP 200
DELETE /user-role-assignments/delete/{idAssignment}                              -> HTTP 200
```

Trois enseignements que l'investigation manuelle n'avait pas donnés :

1. **Les actions de machine à états étaient toutes exploitables.** `valider`, `expedier`,
   `receptionner`… acceptaient l'id d'un agrégat d'un autre tenant. Celles qui recevaient déjà
   `organizationId` (`reserve`, `deliver`, `ship`, `receive`, `receiveLine`) ne s'en servaient que
   pour `hasPermission` — or une affectation **GLOBAL dans sa propre organisation** matche
   n'importe quel `scopeId` : exactement le contournement trouvé en 5b-2b, resté ouvert sur une
   douzaine d'actions.
2. **`/reporting/stock/site/{idSite}` fuyait pour la même raison** : `requireSiteAccess` vérifiait
   la permission mais jamais l'appartenance du site.
3. **`DELETE /user-role-assignments/{id}` était la plus grave** : gardée par `RBAC_MANAGE`, que
   **tout** admin de tenant possède via le bootstrap — B supprimait l'affectation RBAC de A.

## 3. Correctifs

- **7 deletes** (+ `sites`) : nouvelle surcharge `delete(id, organizationId)` qui recharge
  l'entité, vérifie l'appartenance, puis délègue au `delete(id)` existant — les règles métier
  préexistantes (`SITE_ALREADY_IN_USE`, `CUSTOMER_ALREADY_IN_USE`, la
  `DataIntegrityViolationException` d'Article) sont **conservées intactes**.
- **Actions d'agrégat** (CustomerOrder ×6, PurchaseOrder ×3, StockTransfer ×6) et **`findLines`**
  ×4 : appartenance en tête via le `findById(id, organizationId)` **déjà scopé** — réutilisation
  du code éprouvé plutôt qu'une nouvelle vérification à maintenir.
- **City / Warehouse** : lectures scopées pour la **première fois** (`findById`, `findByCode`,
  `findAll`). Ces deux entités n'avaient **jamais** été couvertes par 5b-2a/b/c.
- **`/cities/filter/organization/{id}`** : l'organisation du **chemin est désormais ignorée**,
  seul le principal fait foi.
- **Reporting** : `requireSiteInOrganization` **avant** `requireSiteAccess`.
- **`updatePhoto`** ×3 : prend `organizationId` et vérifie l'appartenance.
- **`/articles/historique/**`** ×3 : l'article doit appartenir à l'appelant.
- **URA delete** : appartenance **avant** `requireRbacManage` (même ordre qu'en 5b-2c).
- **Legacy `/utilisateurs/**`** : `findById`, `findByEmail`, `findAll`, `delete` passent par les
  variantes scopées. Hardening volontaire bien que 4e doive supprimer ces routes : laisser une
  faille de suppression inter-tenant ouverte en attendant un incrément ultérieur n'était pas
  acceptable.
- **Supprimés** : `DELETE /roles/delete/{id}` et `DELETE /permissions/delete/{id}` — catalogues
  globaux, `RBAC_MANAGE` ne distingue pas les tenants. Méthodes de service conservées.

## 4. La matrice

Deux tests. `noEndpointLetsOneTenantTouchAnotherTenantResources` sonde **71 appels** inter-tenant
et vérifie en plus que les 11 ressources de A restent lisibles par A (absence d'effet de bord).
`everyEndpointIsEitherProbedOrJustifiablyExcluded` énumère le
`RequestMappingHandlerMapping` et échoue si un endpoint n'est ni sondé ni exclu avec justification.

Trois modes de sonde, parce qu'un seul critère produisait de faux positifs :

| Mode | Attendu | Usage |
|---|---|---|
| `DENY` | 4xx | tout accès par id — le cas général |
| `EMPTY` | 200 + corps vide | listes filtrées par organisation : répondre 200 avec `[]` est **correct**, pas une fuite |
| `NO_FOREIGN_DATA` | le corps ne contient pas un marqueur de A | `/cities/filter/organization` ignore désormais le chemin et renvoie les villes de **l'appelant** : exiger un corps vide serait faux |

**Faux positif que j'ai dû corriger** : mon premier marqueur `NO_FOREIGN_DATA` était `"id":2,`,
qui matchait un `organization.id` imbriqué quelconque. Remplacé par un **nom de ville unique** par
tenant, marqueur non ambigu. Sans ça la matrice échouait sur un endpoint correct.

## 5. Critères de sortie

| Critère | Statut |
|---|---|
| 7 deletes scopés (+ sites, + legacy utilisateurs) | **Vérifié** |
| `roles/delete` et `permissions/delete` supprimés, services conservés | **Vérifié** |
| `users`/`tenants`/`organizations`/URA delete vérifiés, URA corrigé | **Vérifié** |
| Matrice automatique en place, verte | **Vérifié** — 71 sondes + 11 contrôles d'effet de bord |
| Tout endpoint sondé ou exclu avec justification | **Vérifié** — test dédié |
| Chaque delete a un test rouge→vert | **Partiellement — voir §7** |
| Suite complète verte | **Vérifié** — 257 tests (255 avant) |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test affaibli, aucun `@Disabled` | **Vérifié** |

## 6. Tests existants modifiés

| Test | Modification | Justification |
|---|---|---|
| `UtilisateurAuthenticationCharacterizationTest` | nouveau helper `createUserInCallerTenant` pour les 2 tests qui relisent la ressource ; `createUser` **inchangé** | les routes legacy sont désormais scopées ; un utilisateur sans `entreprise` est invisible en lecture scopée. `loginSucceedsAndJwtCarriesExpectedClaims` caractérise justement ce cas et **garde** `createUser` — j'ai d'abord modifié `createUser` globalement, ce qui a cassé ce test, d'où la séparation en deux helpers |
| `PurchaseOrder`/`CustomerOrder`/`StockTransfer`/`CrossModule*` IntegrationTest | ajout de `organizationId` aux appels | adaptation mécanique de signature, aucune assertion touchée |
| `catalog/application/package-info.java` (nouveau) | `@NamedInterface("application")` | `purchasing`/`sales` appellent `ArticleService` pour vérifier l'appartenance de l'article ; `ModularityTests` l'a refusé (type non exposé). Exposer le package est la façon prévue par Modulith, et il y a précédent (`organization.application`, `identity.application.dto`) |

## 7. Limites assumées (non vérifié)

- **Pas de test rouge→vert individuel par delete.** La matrice a servi de rouge collectif : elle a
  listé les 30 fuites avant correctif et est verte après. C'est une preuve réelle, mais ce n'est
  pas 7 tests nommés comme le demandait la consigne. Les deletes des 7 ressources **sont** couverts
  par la matrice (sondes `DELETE` + contrôle d'absence d'effet de bord), pas par un test dédié
  portant leur nom. Écart assumé et signalé plutôt que maquillé.
- **La matrice ne prouve que l'isolation inter-tenant**, pas l'absence d'autres défauts (permission
  métier intra-organisation, validation). C'est l'objet de 5c.
- **Le `tenants/delete` n'a pas été retiré.** La consigne proposait de le supprimer par défaut s'il
  n'avait pas de sens clair. Il est **déjà self-only** depuis 5b-2c (vérifié : un tenant ne peut
  supprimer que lui-même) et la matrice confirme qu'il refuse l'id d'autrui. Le supprimer serait un
  changement de contrat sans gain de sécurité ; je l'ai donc **conservé** — décision contraire au
  défaut suggéré, consignée ici pour être revue.

## 8. Backlog découvert

- **Références imbriquées toujours non vérifiées** (déjà signalé en 5b-2g) : `Article.category`,
  `Sale.customerId`, `CustomerOrder.customerId`, `PurchaseOrder.supplierId`, `article` des lignes.
  La matrice ne les détecte pas : elle teste l'id de la **ressource**, pas ses **références**.
  Gravité moyenne.
- **`POST /purchase-orders/{id}/lignes/{idLigne}/receptionner`** : l'appartenance de la *commande*
  est vérifiée, mais pas que `idLigne` appartient bien à cette commande… si : un contrôle
  préexistant le fait (`PURCHASE_ORDER_LINE_NOT_FOUND`). Vérifié, rien à faire.
- **`/reporting/stock/low`** et **`/reporting/stock/global`** filtrent par RBAC mais ne sont pas
  sondés (aucun id en entrée). Leur filtrage repose entièrement sur `canAccessSite` — non
  re-vérifié dans cet incrément.
