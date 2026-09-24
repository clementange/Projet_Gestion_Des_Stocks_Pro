# Phase 5b-2b — lecture scoping par organisation, entités à dérivation via Site — Rapport

Deuxième des trois incréments de 5b-2 (voir docs/phase-5b1-report.md §8 et
docs/phase-5b2a-report.md). Couvre les 6 entités dont l'organisation n'est
jamais portée directement mais dérivée via `Site -> City -> Organization` :
`Sale`, `CustomerOrder`, `PurchaseOrder`, `StockTransfer` (deux sites :
origine et destination), `Site` lui-même, et les deux lectures de `Stock`
exposées directement par `InventoryController` (`getStock`, `findMovements`).

## 0. Investigation préalable (exigée avant tout code, par incrément)

Deux pièges identifiés en Phase 5b-2a ont été vérifiés pour ces 6 entités,
sans présumer par analogie :

**Piège 1 — la création force-t-elle déjà l'organisation/le site depuis le
principal ?** Réponse : non, et le mécanisme lui-même ne peut pas être le
même qu'en 5b-2a. `Site` est un paramètre métier explicite et légitimement
fourni par le client (un vendeur choisit son site de vente, pas l'inverse) —
il ne peut pas être "forcé depuis le principal" comme `organizationId` l'a
été pour Article/Category/Customer/Supplier. Le vrai gap n'est donc pas
"organisation jamais enregistrée" mais **"aucune vérification que le site
fourni par le client appartient réellement à l'organisation de
l'appelant"** — un gap de nature différente, propre à cet incrément.

Constat par entité, confirmé par lecture directe du code (pas supposé) :
- `SaleServiceImpl.create()` avait déjà un `hasPermission(..., ScopeType.SITE,
  siteId, organizationId)`, mais avec un contournement GLOBAL non détecté
  jusqu'ici (voir §1).
- `CustomerOrderServiceImpl.create()`, `PurchaseOrderServiceImpl.create()`,
  `StockTransferServiceImpl.create()` n'avaient **aucune** vérification de
  permission ni d'appartenance de site — confirme et étend le gap RBAC déjà
  catalogué en Phase 5b-1 §8 ("aucune vérification de permission sur les
  chemins de création").
- `SiteController.save()` n'a aucune vérification que la `City` référencée
  appartient à l'organisation de l'appelant — chaîne `City -> Organization`
  plus profonde que le périmètre des 6 entités nommées, **délibérément
  laissée ouverte** (voir §4).

**Piège 2 — existe-t-il des appelants internes cross-module qui doivent
garder une variante non filtrée ?** Recherche exhaustive par `grep` avant
toute modification :
- `SiteService.findById(Long)` : appelé en interne par
  `ReportingServiceImpl.getSalesSummary` — **conservé tel quel**, nouvelle
  variante `findById(Long, Long organizationId)` ajoutée pour le point
  d'entrée HTTP. Réutilisé aussi par les nouvelles vérifications
  d'appartenance de site à la création (Sale/CustomerOrder/PurchaseOrder/
  StockTransfer), qui doivent charger le site avant de connaître son
  organisation.
- `SaleService.findLines`/`findAllBySiteAndPeriod` : appelés en interne par
  `ReportingServiceImpl`, déjà protégés par le RBAC scope-organisation de
  5b-1 — **non modifiés**.
- `CustomerOrderService`/`PurchaseOrderService`/`StockTransferService.findById/
  findByCode/findAll` : aucun appelant interne (seuls les contrôleurs REST
  correspondants) — signatures changées directement, sans variante à
  conserver.

## 1. Découverte additionnelle : contournement GLOBAL sur `Sale.create`

`SaleServiceImpl.create()` vérifiait déjà `hasPermission(userId, SALE_CREATE,
ScopeType.SITE, dto.getSite().getId(), organizationId)`. Mais
`AuthorizationServiceImpl.hasPermission` (Phase 5b-1) filtre d'abord les
affectations du caller sur **sa propre** organisation, puis applique la
logique GLOBAL préexistante sans jamais revérifier que le `scopeId` demandé
appartient à cette organisation. Conséquence : un appelant avec une
affectation `GLOBAL` dans l'organisation A pouvait fournir n'importe quel
`siteId`, y compris un site appartenant à l'organisation B, et
`hasPermission` répondait `true` — GLOBAL matche "n'importe quel scope"
littéralement, y compris un scope hors de l'organisation du caller.

Ce n'est pas un bug indépendant : c'est une conséquence mécanique directe du
correctif de 5b-1 (qui filtre par organisation avant d'appliquer GLOBAL) sur
un paramètre (`siteId`) que rien ne validait déjà contre l'organisation de
l'appelant. `requireSiteInOrganization` (voir §2) ferme ce contournement
**indépendamment** de `hasPermission` — la vérification d'appartenance de
site tourne avant et ne dépend d'aucune logique RBAC.

## 2. Conception

**Lecture** (`findById`/`findByCode`/`findAll`) : même règle qu'en 5b-2a,
appliquée via la chaîne dérivée `entité -> Site -> City -> Organization` au
lieu d'un champ direct. Match fail-closed (les deux côtés non-null et
égaux) ; mismatch sur `findById`/`findByCode` lève le même `*_NOT_FOUND`
qu'un id inexistant (aucune fuite d'existence) ; `findAll` filtre la liste.
`StockTransfer` a deux sites (origine ET destination) : `belongsToOrganization`
exige que les **deux** appartiennent à l'organisation de l'appelant — un
transfert dont un seul site serait dans l'organisation ne doit pas être
visible non plus.

**Création** (`requireSiteInOrganization`, une méthode privée par service,
même forme dans les 4 services) : charge le site via
`siteService.findById(siteId)` (variante non filtrée, interne), vérifie
`site.getCity().getOrganization().getId().equals(organizationId)`, lève
`InvalidOperationException(*_ACCESS_DENIED)` sur mismatch — réutilise le
code d'erreur `*_ACCESS_DENIED` déjà existant pour chaque entité (`SALE_
ACCESS_DENIED`, `CUSTOMER_ORDER_ACCESS_DENIED`, `PURCHASE_ORDER_ACCESS_
DENIED`, `STOCK_TRANSFER_ACCESS_DENIED`), pas de nouveau code. Placée
**avant** toute vérification `hasPermission` existante (Sale) pour rester
indépendante de la logique RBAC. `StockTransfer.create` appelle cette
vérification **deux fois**, une pour `originSite`, une pour
`destinationSite` — les deux sites doivent appartenir à l'organisation de
l'appelant, indépendamment l'un de l'autre.

`CustomerOrder.create`/`PurchaseOrder.create`/`StockTransfer.create`
n'avaient aucune vérification de permission avant cet incrément (voir §0) :
la nouvelle vérification d'appartenance de site ajoute une frontière
tenant étroite, sans tenter de résoudre le gap RBAC plus large "qui peut
créer" déjà catalogué et délibérément reporté (docs/migration-notes.md,
Phase 5b-1 §8 et Phase 0 "Constats RBAC/permissions").

**`InventoryController`** : découverte non cataloguée jusqu'ici pendant
cette investigation — les 5 endpoints (`getStock`, `findMovements`,
`receive`, `issue`, `correct`) n'avaient **aucune** vérification de
principal/permission, plus sévère que les autres gaps puisqu'elle inclut 3
endpoints d'**écriture** cross-tenant sans restriction. Décision de
périmètre (validée avant implémentation) : corriger les 2 lectures
(`getStock`/`findMovements`, en réutilisant `siteService.findById(idSite,
organizationId)` directement — pas de méthode dédiée, le site suffit) dans
le cadre de la couverture "Stock" de 5b-2b ; laisser les 3 écritures
(`receive`/`issue`/`correct`) hors périmètre, cataloguées en backlog (§4).

## 3. Root cause additionnelle trouvée en vérifiant (tests HTTP existants)

Après implémentation, `./mvnw clean verify` a révélé 3 échecs (sur 210
tests) — tous dans des fixtures de test, pas dans le code de production.
Root cause commune : plusieurs tests HTTP (`BusinessModulesHttpIntegrationTest`,
`OrganizationControllersHttpIntegrationTest`) créent une **nouvelle**
`Organization` via `POST /organizations/create` (endpoint toujours sans
aucune vérification — cas spécial explicitement reporté à 5b-2c) pour
construire leur hiérarchie Site/City de test, alors que le principal
authentifié a déjà sa **propre** organisation (résolue une fois à
l'inscription du tenant, portée par son JWT). Avant cet incrément, rien ne
vérifiait qu'un site appartenait à l'organisation de l'appelant : cette
fixture fonctionnait par accident. Une fois la vérification en place, le
site construit sous la organisation fraîchement créée n'appartient plus à
l'organisation du principal — rejeté à raison.

Corrigé en décodant `organizationId` directement du payload JWT (les tests
en possèdent déjà le jeton) et en construisant la hiérarchie City/Site sous
cette organisation plutôt que d'en créer une nouvelle — reflète l'usage réel
(un admin de tenant opère dans sa propre organisation), pas un contournement
de l'assertion observée. `stockTransferFullLifecycleWorksOverRealHttp`,
`fullSupplyChainFlowWorksOverRealHttpWithAuthenticatedPrincipal` et
`fullOrganizationHierarchyCreationWorksOverHttp` corrigés selon ce principe.

## 4. Hors périmètre, délibérément reporté

- **`InventoryController.receive/issue/correct`** (écriture, zéro
  vérification) — voir §2, nouvellement catalogué, plus sévère que les gaps
  déjà connus.
- **`SiteController.save()`** — aucune vérification que la `City` référencée
  appartient à l'organisation de l'appelant ; javadoc explicite ajoutée sur
  la classe.
- **`OrganizationController`** (`save`/`findById`/`findAll`/`delete`) —
  aucune vérification d'aucune sorte ; racine du problème de fixture
  rencontré en §3. Cas spécial "self-only" déjà prévu pour 5b-2c.
- Le gap RBAC plus large "qui peut créer une vente/commande/transfert" (pas
  seulement "sur quel site") reste celui déjà catalogué en Phase 5b-1 §8 et
  Phase 0 — non traité ici, la vérification d'appartenance de site est
  indépendante et plus étroite.

## 5. Tests

- `crossOrganizationReadsAreScoped` (un par entité : `SaleServiceIntegrationTest`,
  `CustomerOrderServiceIntegrationTest`, `PurchaseOrderServiceIntegrationTest`,
  `StockTransferServiceIntegrationTest`, `OrganizationModuleIntegrationTest`
  pour `Site`) — même schéma qu'en 5b-2a : organisation A et B, entité créée
  dans A, lecture réussie depuis A, `EntityNotFoundException`/liste vide
  depuis B.
- `createShouldRejectSiteFromAnotherOrganization` (CustomerOrder,
  PurchaseOrder) et `createShouldReject{Origin,Destination}SiteFromAnotherOrganization`
  (StockTransfer, les deux sites testés indépendamment) — prouvent que la
  nouvelle vérification ferme le gap "zéro check" trouvé en §0.
- `createShouldRejectSiteFromAnotherOrganizationEvenWithGlobalScope` (Sale)
  — reproduit précisément le contournement GLOBAL décrit en §1 (affectation
  GLOBAL dans l'organisation A, site appartenant à l'organisation B) et
  vérifie qu'il est désormais rejeté (`SALE_ACCESS_DENIED`), pas silencieusement
  autorisé.
- `inventoryReadsRejectSiteFromAnotherOrganization`
  (`BusinessModulesHttpIntegrationTest`) — HTTP de bout en bout, deux
  tenants réels, `getStock`/`findMovements` sur le site d'un autre tenant
  rejetés en 404 `SITE_NOT_FOUND`, lecture par le tenant propriétaire
  toujours fonctionnelle.

## 6. Critère de sortie

| Critère | Résultat |
|---|---|
| `findAll`/`findById`/`findByCode` filtrés par organisation, pour les 6 entités (dérivation via Site) | **Vérifié** — 5 tests dédiés (StockTransfer teste les deux sites). |
| Mismatch = même 404 qu'un id inexistant, pas de fuite d'existence | **Vérifié**. |
| Vérification d'appartenance de site à la création, pour Sale/CustomerOrder/PurchaseOrder/StockTransfer | **Vérifié** — 5 tests dédiés, dont le contournement GLOBAL. |
| `getStock`/`findMovements` scopés à l'organisation de l'appelant | **Vérifié** — 1 test HTTP de bout en bout, deux tenants réels. |
| `findById(Long)`/`findLines`/`findAllBySiteAndPeriod` (appelants internes hors périmètre) toujours fonctionnels | **Vérifié** — `ReportingServiceImpl` et les tests existants restent verts sans modification de comportement. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 221 tests, 0 échec, 0 erreur (210 avant l'incrément, +11 tests dédiés). |

## 7. Fichiers modifiés

**Production** : `organization/application/SiteService.java`,
`organization/application/impl/SiteServiceImpl.java`,
`organization/presentation/rest/SiteController.java`,
`sales/application/{SaleService,CustomerOrderService}.java`,
`sales/application/impl/{SaleServiceImpl,CustomerOrderServiceImpl}.java`,
`sales/presentation/rest/{SaleController,CustomerOrderController}.java`,
`purchasing/application/PurchaseOrderService.java`,
`purchasing/application/impl/PurchaseOrderServiceImpl.java`,
`purchasing/presentation/rest/PurchaseOrderController.java`,
`transfers/application/StockTransferService.java`,
`transfers/application/impl/StockTransferServiceImpl.java`,
`transfers/presentation/rest/StockTransferController.java`,
`inventory/presentation/rest/InventoryController.java`.

**Test** : `organization/OrganizationModuleIntegrationTest.java`,
`sales/SaleServiceIntegrationTest.java`,
`sales/CustomerOrderServiceIntegrationTest.java`,
`purchasing/PurchaseOrderServiceIntegrationTest.java`,
`transfers/StockTransferServiceIntegrationTest.java`,
`integration/BusinessModulesHttpIntegrationTest.java` (+ correctif de
fixture, voir §3), `organization/OrganizationControllersHttpIntegrationTest.java`
(+ correctif de fixture, voir §3), `integration/CrossModuleConcurrencyIntegrationTest.java`,
`integration/CrossModuleEndToEndIntegrationTest.java` (adaptation mécanique
des signatures changées, aucun comportement testé modifié).

**Docs** : `docs/migration-notes.md`.

## 8. Prochaine étape

5b-2b terminé et vérifié vert. Reste 5b-2c (cas spéciaux : `Organization`/
`Tenant` "soi-même uniquement", `User` via `Tenant.id`, `Role`/`Permission`)
— englobe aussi `OrganizationController`, racine du problème de fixture
rencontré en §3. **N'enchaîne pas sur un autre incrément sans feu vert
explicite.**
