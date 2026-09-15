# Finition post-Phase-3c — Incrément B — Rapport

Sujet : vérifier qu'aucun agrégat à invariant métier — parmi les 21
entités des 8 modules neufs, et parmi les agrégats du legacy plat
(`model/`) — n'a été laissé sans golden master pur (test JUnit sans
contexte Spring, introduit systématiquement en Phase 3c dès qu'une entité
portait une méthode de domaine non triviale).

**Conclusion : tout est déjà couvert. Aucun test superflu créé, aucun
fichier de code modifié.**

## 1. Méthode de vérification

Plutôt que de me fier à la liste mémorisée des incréments Phase 3c, scan
mécanique et exhaustif : recherche de toute signature de méthode
manuscrite (`public`/`private`/`protected`, hors constructeurs générés par
Lombok) dans chaque fichier `.java` des packages `domain/model/` des 8
modules neufs, puis dans `model/` (legacy plat). Une classe `@Data` pure
(Lombok génère uniquement getters/setters/`equals`/`hashCode`/
constructeurs) ne fait remonter aucun résultat ; une classe à invariant en
fait remonter une par méthode de domaine.

## 2. Modules neufs — 21 entités, aucune omise

`find src/main/java -path "*/domain/model/*.java"` retourne 32 fichiers :
21 entités + 4 `package-info.java` + 7 enums de statut/type (`SiteType`,
`ScopeType`, `StockMovementSource`, `StockMovementType`,
`PurchaseOrderStatus`, `CustomerOrderStatus`, `TransferStatus`) — compte
qui correspond exactement à la liste des 21 entités caractérisée en
Phase 2 (`docs/phase-2-report.md` §2), rien de plus, rien de moins.

Le scan de méthodes manuscrites ne fait remonter que **5 entités**, toutes
déjà dotées d'un golden master écrit pendant leur incrément Phase 3c
respectif :

| Entité | Méthodes de domaine | Golden master | Incrément |
|---|---|---|---|
| `inventory.Stock` | `issue`/`receive`/`reserve`/`releaseReservation`/`correct`/`getQuantiteDisponible` + 3 `require*` privées | `StockTest` (22 tests) | Phase 3c/inventory |
| `purchasing.PurchaseOrder` | `validate`/`cancel`/`requireReceivable`/`markFullyReceived` | `PurchaseOrderTest` (13 tests) | Phase 3c/purchasing |
| `purchasing.PurchaseOrderLine` | `receive`/`getQuantiteRestanteARecevoir`/`isFullyReceived` | `PurchaseOrderLineTest` (9 tests) | Phase 3c/purchasing |
| `sales.CustomerOrder` | machine à états complète (10 méthodes + `requireStatus` privée) | `CustomerOrderTest` (21 tests) | Phase 3c/sales |
| `transfers.StockTransfer` | machine à états complète (9 méthodes + `requireStatus` privée) | `StockTransferTest` (20 tests) | Phase 3c/transfers |

Les 16 autres entités (`Organization`/`City`/`Site`/`Warehouse`,
`Article`/`Category`, `Permission`/`Role`/`UserRoleAssignment`,
`StockMovement`, `Supplier`, `Customer`/`CustomerOrderLine`/`Sale`/
`SaleLine`, `StockTransferLine`) ne font remonter aucune signature —
confirmé `@Data` pur, sans invariant, cohérent avec ce qui avait été
observé (et parfois signalé comme écart, ex. `Sale` en Phase 3c/sales)
pendant chaque incrément.

Vérification complémentaire demandée explicitement : aucune entité n'a
"gagné" un invariant depuis son incrément Phase 3c sans golden master
correspondant — le scan porte sur l'état actuel du code, pas sur un
instantané figé au moment de chaque incrément, donc un tel écart aurait
été détecté ici s'il existait. Ce n'est pas le cas.

Vérification des 7 enums de statut (`PurchaseOrderStatus`,
`CustomerOrderStatus`, `TransferStatus`, `StockMovementType`,
`StockMovementSource`, `SiteType`, `ScopeType`) : lus intégralement,
aucun ne porte de méthode — endroits parfois utilisés dans d'autres
projets pour loger de la logique de transition (`isTerminal()`,
`canTransitionTo()`), mais ici les transitions sont entièrement portées
par les agrégats (`CustomerOrder.requireStatus`,
`StockTransfer.requireStatus`), pas par les enums eux-mêmes. Rien à
couvrir séparément.

## 3. Legacy plat (`model/`) — aucun agrégat à invariant trouvé

12 classes entités dans `model/` (hors `AbstractEntity`, `Adresse` —
superclass/embeddable déjà couverts par les modules neufs, et les 3 enums
`EtatCommande`/`SourceMvtStk`/`TypeMvtStk`) : `Client`, `CommandeClient`,
`CommandeFournisseur`, `Entreprise`, `Fournisseur`,
`LigneCommandeClient`, `LigneCommandeFournisseur`, `LigneVente`,
`MvtStk`, `Roles`, `Utilisateur`, `Ventes`.

Même scan mécanique appliqué : **zéro signature de méthode manuscrite
trouvée sur les 12.** Toutes sont des classes `@Data`/`@Entity` pures,
sans méthode de domaine. Cohérent avec l'état documenté du dépôt : les
Phases 19-23 ont progressivement re-backé la logique métier de
`Ventes`/`CommandeClient`/`CommandeFournisseur`/`MvtStk`/`Article` sur les
agrégats des modules neufs correspondants (`sales.Sale`,
`sales.CustomerOrder`, `purchasing.PurchaseOrder`,
`inventory.Stock`/`StockMovement`) — ces derniers portent déjà, quand ils
en ont, l'invariant réel et le golden master associé (tableau §2). Le
legacy plat ne fait plus que porter la forme JSON/DB du contrat HTTP
historique ; ses `*ServiceImpl` délèguent aux services des modules neufs
plutôt que de recalculer une logique propre (vérifié par lecture directe
de `MvtStkServiceImpl` dans l'Incrément A, et cohérent avec les
commentaires en tête de `CommandeFournisseurServiceImpl`/
`CommandeClientServiceImpl` déjà lus en Phase 3c).

Il n'y a donc, à ce jour, **aucun agrégat legacy avec une logique métier
non testée en isolation** — le legacy plat n'a jamais porté ce genre de
logique depuis son adaptation en couche fine sur les modules neufs.

## 4. Décision

Aucun test créé. Créer un golden master sur une des 12 classes legacy
plates ou sur l'une des 16 entités `@Data` pures des modules neufs
n'aurait rien à couvrir (pas d'invariant, donc pas de comportement à
figer au-delà de ce que `@Data` génère déjà mécaniquement) — un tel test
serait un test superflu au sens où l'incrément le proscrit explicitement.

## 5. Fichiers modifiés

Aucun (vérification uniquement, ce rapport excepté).

## 6. Prochaine étape

Incrément B terminé, périmètre de finition post-Phase-3c clos. Prêt pour
la discussion Phase 4 (extinction du legacy plat).
