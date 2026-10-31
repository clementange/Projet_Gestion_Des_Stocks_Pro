# Phase 5b-2g — « create n'est jamais update » sur les 10 endpoints restants — Rapport

Objectif : ramener `CreateEndpointIdGuardTest.KNOWN_UNGUARDED_DEBT` à **zéro** sans supprimer la
capacité de modifier les ressources qui n'avaient aucun autre chemin d'écriture.

## 1. Investigation (avant tout code)

| Endpoint | (a) endpoint d'update ? | (b) pré-check d'unicité s'excluant lui-même ? | (c) organisation forcée depuis le principal ? | (d) entités imbriquées acceptées / rechargées |
|---|---|---|---|---|
| `/articles/create` | **non** | non (aucun pré-check) | oui | `category`, `organization` — **category jamais rechargée ni vérifiée** |
| `/categories/create` | **non** | non | oui | `organization` |
| `/cities/create` | **non** | non | **non** | `organization` — **fournie librement par le client** |
| `/warehouses/create` | **non** | non | **non** | `site` — **rechargé** (contrôle de type ENTREPOT) mais propriétaire jamais vérifié |
| `/customers/create` | **non** | **oui** (`!existing.getId().equals(dto.getId())`) → upsert voulu | oui | `adresse` (embedded) |
| `/suppliers/create` | **non** | **oui** (idem) → upsert voulu | oui | `adresse` (embedded) |
| `/sales/create` | **non** (actions d'état) | non (rejette tout code existant) | n/a — `site` vérifié (5b-2b) | `site` vérifié ; `customerId`, lignes→`article` **non vérifiés** |
| `/customer-orders/create` | **non** (actions d'état) | non | n/a — `site` vérifié | idem, `customerId` non vérifié |
| `/purchase-orders/create` | **non** (actions d'état) | non | n/a — `site` vérifié | idem, `supplierId` non vérifié |
| `/stock-transfers/create` | **non** (actions d'état) | non | n/a — 2 sites vérifiés | lignes→`article` non vérifié |

Constat central : **aucun des 10 n'avait d'endpoint d'update**. Poser la garde sans rien d'autre
aurait donc supprimé le seul moyen de modifier 6 d'entre eux — d'où les nouveaux `PUT`.

## 2. Rouges observés (étape 2)

`CreateIdInjectionAllEndpointsHttpIntegrationTest`, 10 tests, un par endpoint. **10/10 rouges**,
tous `Status expected:<400> but was:<200>` : le détournement réussissait partout.

```
createArticleRejectsClientSuppliedId          expected:<400> but was:<200>
createCategoryRejectsClientSuppliedId         expected:<400> but was:<200>
createCityRejectsClientSuppliedId             expected:<400> but was:<200>
createWarehouseRejectsClientSuppliedId        expected:<400> but was:<200>
createCustomerRejectsClientSuppliedId         expected:<400> but was:<200>
createSupplierRejectsClientSuppliedId         expected:<400> but was:<200>
createSaleRejectsClientSuppliedId             expected:<400> but was:<200>
createCustomerOrderRejectsClientSuppliedId    expected:<400> but was:<200>
createPurchaseOrderRejectsClientSuppliedId    expected:<400> but was:<200>
createStockTransferRejectsClientSuppliedId    expected:<400> but was:<200>
```

Puis **10/10 verts** après la garde. Chaque test vérifie aussi que la ressource de A est
inchangée et toujours lisible par A.

## 3. Correctif

- `CreationIdGuard.requireNoId` en tête des 10 `create`.
- **6 nouveaux `PUT /<ressource>/update/{id}`** (articles, categories, cities, warehouses,
  customers, suppliers). Motif identique partout : l'entité est **rechargée**, l'appartenance à
  l'organisation du principal vérifiée **avant toute écriture** (404 fail-closed sinon), l'id vient
  du **chemin**, et l'organisation est **réinjectée depuis l'entité existante** avant validation —
  le corps ne peut donc ni la changer ni l'effacer. Les champs sont appliqués sur l'entité chargée,
  jamais via `toEntity(dto)` : un id ou une organisation venant du client ne peut pas atteindre
  `save()`.
- **Pas d'update pour sales / customer-orders / purchase-orders / stock-transfers** : agrégats à
  machine d'états, modifiés par leurs actions métier (`valider`, `annuler`, `expedier`,
  `receptionner`…). La garde de création suffit.
- `/cities/create` : organisation **forcée depuis le principal** (toujours écrasée).
- `/warehouses/create` : `WarehouseService.save` prend désormais `organizationId` et vérifie que le
  `Site` **rechargé** appartient à l'appelant, **avant** l'invariant de type ENTREPOT.
- Pré-checks d'unicité de mail (customers/suppliers) déplacés dans `update`, en conservant
  l'auto-exclusion (`!other.getId().equals(id)`) : garder son propre mail reste autorisé.
- `KNOWN_UNGUARDED_DEBT` **vidée**, `GUARDED` complétée (14 endpoints), et nouveau test
  `theDebtListMustStayEmpty()` qui casse le build si la dette est réalimentée.

## 4. Régression que j'ai introduite puis corrigée (à lire)

Les trois `updatePhoto` (Article, Customer, Supplier) faisaient
`dto = findById(id); dto.setPhoto(url); return save(dto);` — un aller-retour par le chemin de
**création** avec un id. Ma garde les a donc cassés : 3 tests de
`PhotoAttachmentCharacterizationTest` sont passés au rouge (400 au lieu de 200).

Corrigé en mutant l'entité **chargée** (`repository.save(existing)`), exactement le motif déjà
appliqué à `UserServiceImpl.updatePhoto` en Phase 4c — où ce même aller-retour corrompait le mot de
passe. Ce n'est donc pas un contournement de ma garde mais la suppression d'un motif dangereux
resté en place sur trois services.

**J'ai aussi cassé le contexte Spring une fois** : mon script de génération a laissé `{{id}}`
littéral dans les `@PutMapping` (template `.format` traité par `.replace`), ce qui a produit
`Invalid mapping pattern ... Not allowed to nest variable captures` et fait échouer les 255 tests au
démarrage. Corrigé ; mentionné ici parce qu'un rapport qui ne montrerait que l'état final serait
trompeur.

## 5. Tests existants modifiés, et pourquoi

| Test | Modification | Justification |
|---|---|---|
| `CustomerServiceImplTest.shouldAllowUpdatingSameCustomerWithoutMailConflict` | `save(id,…)` → `update(id,…,orgId)`, création avec une organisation réelle | `save()` n'est plus un upsert (décision de l'incrément) ; **l'assertion testée est inchangée** : garder son propre mail ne doit pas être rejeté |
| `SupplierServiceImplTest.shouldAllowUpdatingSameSupplierWithoutMailConflict` | idem | idem |
| `CategoryServiceImplTest.shouldUpdateCategoryWithSuccess` | idem (+ organisation réelle, car `update` exige l'appartenance) | idem ; ce test utilisait `save()` deux fois comme upsert |
| `OrganizationModuleIntegrationTest`, `CrossModuleEndToEndIntegrationTest` | `warehouseService.save(dto)` → `save(dto, organizationId)` | adaptation mécanique de signature, aucun comportement testé modifié |

Aucune assertion affaiblie, aucun `@Disabled`.

## 6. Changements de contrat HTTP (pour le futur frontend)

| Changement | Détail |
|---|---|
| **Nouveau** `PUT /articles/update/{id}` | corps = `ArticleDto` ; `id` et `organization` ignorés du corps |
| **Nouveau** `PUT /categories/update/{id}` | idem |
| **Nouveau** `PUT /cities/update/{id}` | `organization` non modifiable |
| **Nouveau** `PUT /warehouses/update/{id}` | **`site` non modifiable** (voir DÉCISION PAR DÉFAUT) |
| **Nouveau** `PUT /customers/update/{id}` | `organizationId` non modifiable |
| **Nouveau** `PUT /suppliers/update/{id}` | idem |
| **Rupture** : les 10 `POST .../create` | un `id` dans le corps → **400 `CREATION_ID_FORBIDDEN`** (avant : mise à jour silencieuse) |
| **Rupture** : `POST /customers/create`, `POST /suppliers/create` | ne font plus office de mise à jour ; utiliser le `PUT` |
| **Rupture** : `POST /cities/create` | `organization` du corps ignorée (forcée depuis le jeton) |

## 7. DÉCISION PAR DÉFAUT

**`PUT /warehouses/update/{id}` ne permet pas de changer le `site`.** Changer de site déplacerait
l'entrepôt, potentiellement vers une autre organisation, et exigerait de revalider à la fois
l'appartenance du site cible et l'invariant « site de type ENTREPOT ». Le défaut conservateur est de
l'interdire ; si le besoin fonctionnel existe, il mérite son propre incrément.

## 8. Critères de sortie

| Critère | Statut |
|---|---|
| Rouges montrés puis verts, un par endpoint | **Vérifié** — 10/10 rouges `400 attendu / 200 obtenu`, puis 10/10 verts |
| Dette `KNOWN_UNGUARDED_DEBT` = ∅ | **Vérifié** — vidée + `theDebtListMustStayEmpty()` l'impose |
| `GUARDED` complet (14 endpoints) et vérifié sur le bytecode | **Vérifié** — 13 services contrôlés par ArchUnit |
| 6 endpoints d'update créés | **Vérifié** |
| Appartenance vérifiée avant écriture sur chaque update | **Vérifié** (code) ; **NON VÉRIFIÉ PAR UN TEST DÉDIÉ** — voir §10 |
| Cities/Warehouses : organisation/site ne viennent plus du client | **Vérifié** (code) ; test inter-tenant dédié : **non** — voir §10 |
| Suite complète verte | **Vérifié** — 255 tests, 0 échec, 0 erreur (244 avant) |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test affaibli, aucun `@Disabled` | **Vérifié** |

## 9. Fichiers modifiés

**Production** — guards : `ArticleServiceImpl`, `CategoryServiceImpl`, `CityServiceImpl`,
`WarehouseServiceImpl`, `CustomerServiceImpl`, `SupplierServiceImpl`, `SaleServiceImpl`,
`CustomerOrderServiceImpl`, `PurchaseOrderServiceImpl`, `StockTransferServiceImpl`.
Updates : les 6 interfaces + impls + `ArticleController`, `CategoryController`, `CityController`,
`WarehouseController`, `CustomerController`, `SupplierController`.
Divers : `WarehouseService.save` (signature + appartenance de site), `updatePhoto` ×3.

**Test** : `CreateIdInjectionAllEndpointsHttpIntegrationTest` (nouveau, 10 tests),
`CreateEndpointIdGuardTest` (dette vidée + test d'immuabilité), `CustomerServiceImplTest`,
`SupplierServiceImplTest`, `CategoryServiceImplTest`, `OrganizationModuleIntegrationTest`,
`CrossModuleEndToEndIntegrationTest`.

## 10. Limites assumées (non vérifié)

- **Les 6 nouveaux `PUT` n'ont pas de test inter-tenant dédié dans cet incrément.** Le contrôle
  d'appartenance est écrit et lisible dans le code, mais aucun test ne prouve encore qu'un tenant B
  ne peut pas modifier la ressource de A via `PUT`. C'est précisément l'objet de la matrice
  automatique de **5b-2h**, qui couvrira tous les endpoints portant un id de ressource — y compris
  ces six. Tant que 5b-2h n'est pas fait, cette protection est **déclarée, pas prouvée**.
- Idem pour le forçage d'organisation sur `/cities/create` et l'appartenance de site sur
  `/warehouses/create` : implémentés, non couverts par un test inter-tenant dédié.

## 11. Backlog découvert

- **Lectures de `City` et `Warehouse` jamais scopées par organisation.** `CityService` et
  `WarehouseService` n'ont **aucune** méthode prenant un `organizationId` (`findById(Long)`,
  `findAll()`, `findByCode(String)`, `findAllByOrganization(Long)` où l'organisation vient du
  **chemin**). Les phases 5b-2a/b/c n'ont jamais couvert ces deux entités. `GET /cities/{id}`,
  `GET /warehouses/{id}`, `GET /warehouses/all` et
  `GET /cities/filter/organization/{idOrganization}` fuient donc en lecture inter-tenant. Gravité
  **élevée** (lecture seule). Doit être pris par 5b-2h, dont la matrice le révélera.
- **Références imbriquées non vérifiées** : `Article.category`, `Sale.customerId`,
  `CustomerOrder.customerId`, `PurchaseOrder.supplierId`, et les `article` des lignes ne sont jamais
  rechargés pour vérifier qu'ils appartiennent à l'organisation de l'appelant. Un tenant peut donc
  rattacher son article à la catégorie d'un autre tenant. Gravité **moyenne** (pas d'appropriation,
  mais fuite de référence et incohérence inter-tenant). Hors périmètre de 5b-2g (qui porte sur
  l'id de la ressource créée, pas sur ses références).
- `/articles/historique/**` (3 routes) non analysées ici.
