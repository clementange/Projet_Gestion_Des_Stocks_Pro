# Phase 5b-2d — InventoryController writes + SiteController.save City-ownership — Rapport

Incrément supplémentaire proposé et approuvé après 5b-2c, sur deux items du backlog explicitement
catalogués comme ouverts (`docs/phase-5b2b-report.md` §4). Contrairement à 5b-2a/b/c, ce n'est pas
un incrément pré-nommé dans le plan d'origine : périmètre investigué puis proposé, validé par
l'utilisateur avant implémentation (`InventoryController.receive/issue/correct` +
`SiteController.save()`, `OrganizationController.save()` et le backlog RBAC-on-create plus large
explicitement exclus).

## 0. Investigation préalable

Vérifié par lecture directe avant tout code :

- **`InventoryFacade.receive/issue/correct`** : appelés en interne par `SaleServiceImpl`,
  `CustomerOrderServiceImpl`, `PurchaseOrderServiceImpl` — mais leur signature n'est **pas**
  modifiée dans cet incrément (elle porte déjà un paramètre `userId`, jusqu'ici toujours `null`
  côté `InventoryController`). Les nouveaux garde-fous (appartenance de site, permission) vivent
  entièrement dans `InventoryController`, en amont de l'appel à la façade — aucun appelant interne
  affecté.
- **`SiteService.save(SiteDto)`** : aucun appelant interne (confirmé par grep exhaustif, comme en
  5b-2b) — seul `SiteController` l'appelle. Signature changée directement, sans overload à
  conserver.
- **`SiteService.findById(Long, Long)`** (variante org-scopée, 5b-2b) : réutilisable telle quelle
  comme garde-fou d'appartenance de site pour les écritures — elle fait déjà exactement ce qui est
  nécessaire (charge le site, compare son organisation à celle de l'appelant, lève
  `SITE_NOT_FOUND` en cas de mismatch). Pas de nouvelle méthode de service requise côté
  `InventoryController`.

## 1. Découverte critique en implémentant : nouvelles permissions = admins existants bloqués sans migration

`ADMINISTRATEUR` (le rôle bootstrap attribué automatiquement à chaque nouveau tenant) ne
regroupe **que** les codes de permission explicitement seedés par une migration Flyway
(`V3__seed_baseline_rbac_permissions.sql`, `V4__seed_rbac_manage_permission.sql`) — ce n'est pas
une notion "toutes permissions" dynamique. Introduire trois nouveaux codes
(`STOCK_RECEIVE`/`STOCK_ISSUE`/`STOCK_CORRECT`) sans les ajouter à `ADMINISTRATEUR` aurait
immédiatement bloqué **tout** admin de tenant, existant ou nouveau, sur ces trois endpoints —
régression fonctionnelle, pas seulement un durcissement. Corrigé par une nouvelle migration
`V7__seed_stock_adjustment_permissions.sql` (même motif exact que V3/V4 : `INSERT ... ON CONFLICT
DO NOTHING` sur `permission`, puis sur `role_permission` pour `ADMINISTRATEUR`), jamais en
modifiant un fichier `V*` existant (zone gelée CLAUDE.md).

## 2. Conception

**`InventoryController.receive/issue/correct`** — pour chacun des 3 endpoints, dans cet ordre
strict (identique à `requireSiteInOrganization` avant `hasPermission` en 5b-2b, et au garde-fou
d'appartenance de `UserRoleAssignment` avant `requireRbacManage` en 5b-2c) :
1. `siteService.findById(idSite, principal.getOrganizationId())` — garde-fou d'appartenance,
   indépendant, non contournable par la logique RBAC elle-même.
2. `authorizationService.hasPermission(principal.getIdUtilisateur(), <STOCK_RECEIVE|STOCK_ISSUE|STOCK_CORRECT>, ScopeType.SITE, idSite, principal.getOrganizationId())`.

Trois permissions distinctes plutôt qu'un seul code générique `STOCK_ADJUST` : cohérent avec le
grain déjà utilisé partout ailleurs dans le code (`SALE_CREATE`, `PURCHASE_ORDER_RECEIVE`,
`STOCK_TRANSFER_SHIP`/`RECEIVE`, `CUSTOMER_ORDER_RESERVE`/`DELIVER`/`CANCEL` — une permission par
action distincte, jamais un code fourre-tout). `userId` (jusqu'ici toujours `null` passé à la
façade) est désormais résolu depuis le principal — correction directement adjacente, la
traçabilité du mouvement en profite. Nouveau code `ErrorCodes.STOCK_ACCESS_DENIED`.

**`SiteService.save(SiteDto, Long organizationId)`** — nouveau `requireCityInOrganization(Long
cityId, Long organizationId)` privé, dans `SiteServiceImpl` : charge la `City` **fraîche depuis la
base** via `CityService.findById(cityId)` (jamais depuis les données imbriquées potentiellement
fournies par le client dans le DTO — même principe que `requireSiteInOrganization`, qui ne fait
jamais confiance à un objet imbriqué), compare `city.getOrganization().getId()` à
`organizationId`, lève `InvalidOperationException(SITE_ACCESS_DENIED)` (nouveau code) sur
mismatch. **Aucune vérification de permission ajoutée** — question distincte ("qui peut créer un
site"), délibérément hors périmètre, même raisonnement que `CustomerOrder`/`PurchaseOrder`/
`StockTransfer.create()` en 5b-2b (garde-fou d'appartenance seul, pas de gate RBAC).

## 3. Root cause additionnelle trouvée en vérifiant

`./mvnw clean verify` a révélé 2 échecs après implémentation (sur 237 tests) :

- **`BusinessModulesHttpIntegrationTest.inventoryWritesRequirePermissionEvenOnOwnSite`** (test
  ajouté dans cet incrément) : échec sur mon propre test, pas sur le code de production. Root
  cause identifiée en creusant : `POST /utilisateurs/create` (legacy) ne force **jamais**
  `idEntreprise` depuis l'appelant — il faut le fournir explicitement dans le corps de la requête
  (`UtilisateurServiceImpl.create` : `dto.getEntreprise() != null ? dto.getEntreprise().getId() :
  null`). Sans ce champ, l'utilisateur "sans permission" créé pour le test n'appartient à AUCUN
  tenant, et son `organizationId` ne se résout donc jamais correctement au login — la requête
  échouait dès le garde-fou d'appartenance de site (404), pas au niveau de la permission (400)
  comme le test voulait le prouver. Gap déjà connu et délibéré sur le contrat legacy (même posture
  que Phase 4a, jamais durci) — non corrigé côté production, uniquement contourné dans la fixture
  en fournissant explicitement `"entreprise":{"id":...}` (décodé du JWT de l'admin créateur).
- **`OrganizationControllersHttpIntegrationTest.warehouseCreationRejectsSiteTypeSpoofedInPayload`**
  (test préexistant) : même cause racine que les deux fixtures déjà documentées en 5b-2b §3 et
  5b-2c §3 — créait une organisation fraîche via `POST /organizations/create` (toujours sans
  aucune vérification) pour y construire sa hiérarchie City/Site de test. `SiteController.save()`
  vérifiant désormais l'appartenance de la City, la création du site échouait en 400
  `SITE_ACCESS_DENIED` avant même d'atteindre le scénario testé (spoofing du type de site sur
  `WarehouseController`). Même correctif que les deux occurrences précédentes : réutilise
  `organizationIdFromToken` (déjà présent dans ce fichier) au lieu de créer une organisation
  séparée.

## 4. Tests

- `OrganizationModuleIntegrationTest.createSiteShouldRejectCityFromAnotherOrganization` /
  `createSiteSucceedsWhenCityBelongsToCallerOrganization` — garde-fou d'appartenance de City au
  niveau service.
- `BusinessModulesHttpIntegrationTest.inventoryWritesRejectSiteFromAnotherOrganization` — les 3
  endpoints d'écriture rejettent un site d'une autre organisation (404 `SITE_NOT_FOUND`).
- `BusinessModulesHttpIntegrationTest.inventoryWritesRequirePermissionEvenOnOwnSite` — un
  utilisateur du même tenant mais sans `STOCK_RECEIVE` est rejeté (400 `STOCK_ACCESS_DENIED`)
  même sur son propre site ; l'admin (qui a la permission via la migration V7) continue de
  fonctionner, et le mouvement retourné porte désormais le vrai `userId` de l'appelant (plus
  jamais `null`).

## 5. Critère de sortie

| Critère | Résultat |
|---|---|
| receive/issue/correct : appartenance de site vérifiée, indépendante de hasPermission | **Vérifié**. |
| receive/issue/correct : permission dédiée par action (STOCK_RECEIVE/ISSUE/CORRECT) | **Vérifié**. |
| userId réel (plus jamais null) porté par le mouvement enregistré | **Vérifié**. |
| Admins de tenants existants non bloqués par les nouvelles permissions | **Vérifié** — migration V7, `ON CONFLICT DO NOTHING`, idempotente. |
| SiteController.save() : appartenance de City vérifiée, fraîche depuis la base | **Vérifié**. |
| Aucune vérification de permission ajoutée sur SiteController.save() (hors périmètre, délibéré) | **Vérifié**. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 237 tests, 0 échec, 0 erreur (233 avant l'incrément, +4 tests dédiés). |

## 6. Fichiers modifiés

**Production** : `exception/ErrorCodes.java` (+`SITE_ACCESS_DENIED`, +`STOCK_ACCESS_DENIED`),
`inventory/presentation/rest/InventoryController.java`,
`organization/application/SiteService.java`,
`organization/application/impl/SiteServiceImpl.java`,
`organization/presentation/rest/SiteController.java`,
`src/main/resources/db/migration/V7__seed_stock_adjustment_permissions.sql` (nouveau).

**Test** : `organization/OrganizationModuleIntegrationTest.java`,
`organization/OrganizationControllersHttpIntegrationTest.java` (+ correctif de fixture, voir §3),
`integration/BusinessModulesHttpIntegrationTest.java`,
`reporting/ReportingServiceIntegrationTest.java`,
`purchasing/PurchaseOrderServiceIntegrationTest.java`,
`sales/SaleServiceIntegrationTest.java`, `sales/CustomerOrderServiceIntegrationTest.java`,
`transfers/StockTransferServiceIntegrationTest.java`,
`integration/CrossModuleConcurrencyIntegrationTest.java`,
`integration/CrossModuleEndToEndIntegrationTest.java`,
`inventory/InventoryFacadeIntegrationTest.java` (adaptation mécanique des 20 appels
`siteService.save(...)` dont la signature a changé, aucun comportement testé modifié).

**Docs** : `docs/migration-notes.md`.

## 7. Prochaine étape

5b-2d terminé et vérifié vert. Backlog restant, explicitement hors périmètre de cet incrément :
`OrganizationController.save()` (question produit distincte — qui peut créer une organisation),
et le gap RBAC-on-create plus large (Sale/CustomerOrder/PurchaseOrder/StockTransfer/Organization/
Tenant/Article/Category/Customer/Supplier, catalogué depuis Phase 5b-1 §8 et Phase 0).
**N'enchaîne sur aucun de ces points sans feu vert explicite.**
