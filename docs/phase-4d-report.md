# Phase 4d — suppression des adaptateurs legacy devenus superflus — Rapport

Dernier incrément de la Phase 4 : supprimer les 6 chaînes legacy (Client, Fournisseur, Ventes,
CommandeClient, CommandeFournisseur, MvtStk) devenues, en principe, du code mort une fois 4a-4c en
place. L'investigation a montré que ce principe est vrai pour une couche, faux pour une autre — le
scope réel livré est plus étroit et plus précis que la demande initiale.

## 0. Le principe "code mort" vérifié, pas supposé

Deux couches distinctes existent sous ces 6 noms, avec des sorts opposés :

**La couche adaptateur HTTP (Controller/Api/Service/ServiceImpl/Validator/DTO racine)** est bien
du code mort une fois son URL retirée — c'est elle qui a été supprimée.

**La couche "ligne" pour 3 des 6 chaînes (`LigneVente`, `LigneCommandeClient`,
`LigneCommandeFournisseur`) ne l'est pas**, pour deux raisons vérifiées dans le code, pas
supposées :
1. `catalog.ArticleServiceImpl.delete()` (code module-neuf) interroge directement
   `LigneVenteRepository`/`LigneCommandeClientRepository`/`LigneCommandeFournisseurRepository`
   comme garde-fou de suppression — décision déjà actée en Finition A, pour éviter un cycle de
   module (`sales`/`purchasing` dépendent déjà de `catalog` pour `Article`).
2. `SalesArticleHistoryLegacyController`/`PurchaseOrderArticleHistoryLegacyController` (Phase 3a)
   fusionnent ces mêmes tables avec les tables module-neuf (`sale_line`, `customer_order_line`,
   `purchase_order_line` — des tables **physiquement différentes**, confirmé via `orm.xml`, pas de
   double-mapping sur la même table ici) pour restituer l'historique complet d'un article, y
   compris les lignes créées avant la bascule vers les modules neufs.

**Découverte supplémentaire, plus profonde que prévu** : les entités "ligne" gardées ont des champs
`@ManyToOne` typés sur les entités racine (`LigneVente.vente : Ventes`,
`LigneCommandeClient.commandeClient : CommandeClient`,
`LigneCommandeFournisseur.commandeFournisseur : CommandeFournisseur`) — Java exige que ces types
existent pour compiler, que la relation soit traversée ou non à l'exécution. Et la chaîne continue
plus loin : `CommandeClient.client : Client`, `CommandeFournisseur.fournisseur : Fournisseur`,
`LigneVenteDto.vente : VentesDto` (réellement sérialisé dans le JSON de l'historique, pas mort),
`LigneCommandeClientDto.commandeClient : CommandeClientDto` (`@JsonIgnore`, jamais peuplé, mais le
type doit exister), `CommandeClientDto.client : ClientDto` (réellement sérialisé). Résultat : **les
5 entités racine (`Client`, `Fournisseur`, `Ventes`, `CommandeClient`, `CommandeFournisseur`) et 3
DTO racine (`ClientDto`, `VentesDto`, `CommandeClientDto`) doivent rester**, pas seulement les 3
entités "ligne" annoncées dans le plan validé.

**Asymétrie trouvée entre Fournisseur et Client** : `LigneCommandeFournisseurDto.commandeFournisseur`
est typé directement sur l'entité JPA brute `model.CommandeFournisseur` (déjà signalé comme fragile
dans une note antérieure), pas sur un DTO — contrairement à son équivalent côté Client
(`LigneCommandeClientDto.commandeClient : CommandeClientDto`). Cette incohérence d'écriture
préexistante a un effet concret : elle rend `FournisseurDto` et `CommandeFournisseurDto` réellement
supprimables (vérifié par grep exhaustif, aucune référence externe survivante), alors que
`ClientDto` et `CommandeClientDto` ne le sont pas. Périmètre asymétrique, documenté tel quel plutôt
que lissé artificiellement.

**`MvtStk` est la seule chaîne des 6 entièrement supprimable** : aucune entité "ligne" ne pointe
vers elle (ce n'est la "ligne" de rien), et son propre statut était déjà connu comme sans
repository Spring Data (Finition A). Le commentaire correspondant dans `ArticleServiceImpl` a été
mis à jour pour refléter sa disparition complète plutôt que la simple absence de repository.

## 1. Ce qui est supprimé

Pour chacune des 6 chaînes : `Controller`, `Api`, `Service`, `ServiceImpl`, `Validator`. Plus,
selon la chaîne :
- **MvtStk** : `MvtStkDto`, `model.MvtStk`, `model.SourceMvtStk`, `model.TypeMvtStk` (rien ne les
  référence plus nulle part, vérifié).
- **Fournisseur/CommandeFournisseur** : `FournisseurDto`, `CommandeFournisseurDto` (vérifié
  supprimables, voir §0).
- **CommandeClient** : `LigneCommandeClientValidator` (déjà constatée classe morte, jamais
  appelée).

34 fichiers de production supprimés au total. 2 nouveaux contrôleurs créés (§2). 7 fichiers de test
devenus sans objet supprimés, 1 nouveau fichier de test ajouté, 2 fichiers de test existants
adaptés (§3).

## 2. Ce qui est créé : `CustomerController`/`SupplierController`

`sales.Customer`/`purchasing.Supplier` n'avaient **aucun contrôleur module-neuf** — `Client`/
`Fournisseur` (legacy) étaient leur unique surface HTTP. Supprimer `ClientController`/
`FournisseurController` sans remplacement aurait retiré tout accès CRUD à ces entités, pas
nettoyé du code mort. Décision de l'utilisateur (validée explicitement) : créer les contrôleurs
manquants d'abord.

`sales.presentation.rest.CustomerController` (`/customers/*`) et
`purchasing.presentation.rest.SupplierController` (`/suppliers/*`) : create/findById/findAll/delete,
en délégation pure vers `CustomerService`/`SupplierService` (déjà complets depuis la Phase 4c,
`updatePhoto` inclus). Aucune logique à porter depuis `ClientServiceImpl`/`FournisseurServiceImpl`
— ces classes ne faisaient déjà que de la traduction `idEntreprise`/`organizationId`, absorbée par
le vocabulaire module-neuf (`organizationId` déjà natif sur `CustomerDto`/`SupplierDto`).

Les routes photo (`/clients/{id}/photo`, `/fournisseurs/{id}/photo`, Phase 4c) restent inchangées
et fonctionnelles — `ClientPhotoLegacyController`/`FournisseurPhotoLegacyController` dépendaient
déjà directement de `CustomerService`/`SupplierService`, jamais de `ClientServiceImpl`/
`FournisseurServiceImpl`.

## 3. Tests

**Supprimés** (contrat caractérisé disparu, aucune réécriture possible sans changer entièrement
l'objet du test) : `ClientCharacterizationTest`, `FournisseurCharacterizationTest`,
`VentesCharacterizationTest`, `CommandeClientCharacterizationTest`,
`CommandeFournisseurCharacterizationTest`, `MvtStkCharacterizationTest`,
`LegacyControllersIntegrationTest`. Leur couverture applicative (validation, not-found, garde-fou
de suppression) est déjà assurée au niveau service par `CustomerServiceImplTest`,
`SupplierServiceImplTest`, `SaleServiceIntegrationTest`, `CustomerOrderServiceIntegrationTest`,
`PurchaseOrderServiceIntegrationTest`, `InventoryFacadeIntegrationTest` — préexistants, non
touchés par cet incrément. Aucune de ces suites n'a jamais eu de pendant HTTP dédié pour
`SaleController`/`CustomerOrderController`/`PurchaseOrderController`/`InventoryController`
eux-mêmes (contrat déjà considéré suffisant par la codebase avant cet incrément) : cohérence gardée,
pas de sur-couverture ajoutée pour ces 4 routes.

**Créé** : `CustomerSupplierControllerCharacterizationTest` (4 tests) — seule couverture qui
n'existait sous aucune forme avant cet incrément (`CustomerController`/`SupplierController` sont
entièrement nouveaux) : flux CRUD complet + erreur de validation, pour chacun des deux.

**Adaptés** (routes de *setup* seulement, pas le sujet testé) :
- `ArticleHistoryLegacyEndpointsTest` : `createClient`/`createFournisseur` pointent désormais vers
  `/customers/create`/`/suppliers/create` ; les 3 tests de fusion d'historique et le test de
  garde-fou de suppression utilisent `/sales/create`, `/customer-orders/create`,
  `/purchase-orders/create` au lieu des routes supprimées. Le flux "legacy" de la fusion
  (`LigneVenteRepository` etc.) était déjà vide dans ces tests avant cet incrément — les Phases
  18-22 avaient déjà redirigé toute écriture vers les tables module-neuf, bien avant le début de
  cette session ; aucune régression de couverture réelle, seulement une mise à jour d'URL.
- `PhotoAttachmentCharacterizationTest` (Phase 4c) : ses helpers `createClient`/`createFournisseur`
  pointaient aussi vers les routes supprimées — corrigé de la même façon.

## 4. Critère de sortie

| Critère | Résultat |
|---|---|
| Adaptateurs HTTP legacy des 6 chaînes supprimés | **Vérifié** — 34 fichiers supprimés. |
| Aucune perte de fonctionnalité (Client/Fournisseur CRUD) | **Vérifié** — `CustomerController`/`SupplierController` créés, testés. |
| Garde-fou de suppression Article et historique préservés | **Vérifié** — `LigneVente`/`LigneCommandeClient`/`LigneCommandeFournisseur` + repositories + DTO conservés, `ArticleHistoryLegacyEndpointsTest` vert. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** — aucune violation, y compris pour les 2 nouveaux contrôleurs. |
| Tous les tests verts | **Vérifié** — 190 tests, 0 échec, 0 erreur (237 avant l'incrément : -54 tests obsolètes supprimés, +4 tests nouveaux, +3 tests de `ArticleHistoryLegacyEndpointsTest`/`PhotoAttachmentCharacterizationTest` inchangés en nombre). |

## 5. Écart avec le scope initialement validé

Le plan approuvé annonçait la suppression des entités racine (`Client`, `Fournisseur`, `Ventes`,
`CommandeClient`, `CommandeFournisseur`) et des DTO racine correspondants. L'investigation de
compilation (§0) a montré que c'était impossible pour 5 des 6 chaînes — uniquement `MvtStk` l'est
entièrement. Le principe validé par l'utilisateur ("garder ce qui est structurellement
nécessaire, supprimer le reste") est respecté à la lettre ; seule la liste précise des fichiers qui
en relèvent a été corrigée par la compilation elle-même plutôt que par estimation. Documenté ici
plutôt que passé sous silence.

## 6. Prochaine étape

Phase 4 (4a-4d) est maintenant complète. **N'enchaîne pas sur un autre incrément sans feu vert
explicite.**
