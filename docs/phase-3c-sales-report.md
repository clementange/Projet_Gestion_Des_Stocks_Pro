# Phase 3c — module `sales` — Rapport de sortie d'incrément

Sixième module traité pour la séparation domaine/persistance (`Customer`,
`CustomerOrder`, `CustomerOrderLine`, `Sale`, `SaleLine`), le plus gros
module restant en nombre d'entités (5). Même règle : un seul module par
incrément, arrêt et rapport avant le suivant.

## 0. Vérifications préalables (points de vigilance demandés explicitement)

Rien n'a été présumé par analogie avec `purchasing` : les 4 points de
vigilance demandés ont été vérifiés indépendamment avant d'écrire une
ligne de code.

### 1. Consommateurs externes

Recherche exhaustive de `sales.domain.model.{Customer,CustomerOrder,
CustomerOrderLine,Sale,SaleLine}` en dehors de `sales/`. Un seul fichier
externe importe quelque chose du package `sales.domain.model` :
`services/impl/CommandeClientServiceImpl.java` — mais uniquement l'**enum**
`CustomerOrderStatus`, référencé au travers de la couche application/DTO
(`CustomerOrderService`, `CustomerOrderDto`), jamais une des cinq classes
de domaine elles-mêmes, et jamais via une relation JPA. Exactement le même
profil que `purchasing.PurchaseOrderStatus` avec
`CommandeFournisseurServiceImpl`. `orm.xml` n'est donc pas ici une
nécessité technique, mais un choix de cohérence.

### 2. Relations intra-module

- `CustomerOrderLine.customerOrder` → `@ManyToOne` vers `CustomerOrder`
  (même module) + `CustomerOrderLine.article` → `@ManyToOne` vers
  `catalog.Article` (déjà résolu).
- `SaleLine.sale` → `@ManyToOne` vers `Sale` (même module) +
  `SaleLine.article` → `@ManyToOne` vers `catalog.Article` (déjà résolu).
- `CustomerOrder.site` et `Sale.site` → `@ManyToOne` vers
  `organization.Site` (déjà résolu).
- `Customer` n'est référencée par aucune des deux (`CustomerOrder.customerId`
  est un simple `Long`, pas une relation JPA — même pattern que
  `PurchaseOrder.supplierId`).

Ordre retenu, dérivé de ce graphe (entité référencée avant celle qui la
référence, ligne après en-tête) : **`Customer` (spike) → `Sale` →
`SaleLine` → `CustomerOrder` → `CustomerOrderLine`.**

### 3. Deuxième cas `numTel`/`num_tel`

`Customer` a été ajoutée dans la même migration que `Supplier`
(`V5__customer_supplier_and_organization_contact_fields.sql`), avec les
mêmes colonnes de contact. Vérifié colonne par colonne : `customer` porte
bien `num_tel` (avec underscore) en base, alors que l'annotation Java est
`@Column(name = "numTel")` (camelCase, sans underscore) — exactement le
même cas que `Supplier` en Phase 3c/purchasing, même résolution (le nom
littéral de l'annotation est repris tel quel dans le XML,
`SpringPhysicalNamingStrategy` fait la conversion, `ddl-auto=validate`
confirme). Toutes les autres colonnes (`nom`, `prenom`, `photo`, `mail`,
`organizationId`) sont identiques au nom de champ, sans transformation à
vérifier.

### 4. `@Version` / contrainte composite

Recherche de `@Version` et `uniqueConstraints` dans les 5 fichiers de
domaine `sales` — **aucune occurrence**. Recherche des `ALTER TABLE ...
ADD CONSTRAINT ... UNIQUE` dans `V1__initial_schema.sql` pour `sale` et
`customer_order` — un seul `UNIQUE (code)` simple par table (même pattern
que `purchase_order.code`, `site.code`, etc.), aucune contrainte
composite, aucune colonne `version`. Confirmé : ni `@Version` ni
`unique-constraint` composite dans ce module, contrairement à
`inventory.Stock`.

## 1. Étape 0 — invariants métier : écart trouvé vs l'hypothèse de départ

L'hypothèse de départ ("CustomerOrder et Sale, a priori") a été vérifiée
plutôt que prise pour acquise. Lecture des 5 fichiers de domaine :

- **`CustomerOrder`** : machine à états complète et non triviale —
  `validate`, `requireReservable`/`markReserved`,
  `requirePreparable`/`markPrepared`, `requireShippable`/`markShipped`,
  `requireDeliverable`/`markDelivered`, `cancel`, plus un helper privé
  `requireStatus`. **Invariants réels, étape 0 obligatoire.**
- **`Customer`**, **`CustomerOrderLine`**, **`Sale`**, **`SaleLine`** :
  tous des `@Data` purs, aucune méthode de domaine. **`Sale` en
  particulier n'a aucun invariant**, contrairement à l'hypothèse de départ
  qui la supposait à invariants comme `purchasing.PurchaseOrder` (les deux
  ont une structure similaire — code, site, date — mais seul `PurchaseOrder`
  a une machine à états ; `Sale` est une vente au comptant immédiate, sans
  transition d'état modélisée dans le domaine). Écart signalé plutôt que
  silencieusement absorbé.

Une seule des 5 entités nécessitait donc l'étape 0 :
**`sales/domain/model/CustomerOrderTest`** (21 méthodes), écrit et
vérifié avant tout changement de mapping, couvrant chaque transition
d'état (nominale + rejet avec message exact) sur les 7 états
(`BROUILLON`, `VALIDEE`, `RESERVEE`, `PREPAREE`, `EXPEDIEE`, `LIVREE`,
`ANNULEE`).

`CustomerOrder` ne porte pas de `@Builder` Lombok (seulement `@Data`) —
instanciation via `new CustomerOrder()` + setter, comme pour tous les
agrégats précédents sans builder.

- **Avant** tout changement de mapping : **21/21 verts, 0.088 s.**
- **Après** le décrochage domaine/JPA : **21/21 verts, identiques**
  (0.072 s), 0 assertion modifiée.

## 2. Étapes : `Customer` (spike) → `Sale` → `SaleLine` → `CustomerOrder` → `CustomerOrderLine`

### Étape 1 — `Customer` (spike)

Même structure que `purchasing.Supplier` (`@Embedded Adresse`, cas
`numTel`/`num_tel`). Testé avec `CustomerServiceImplTest` (4 tests,
sauvegarde/suppression/erreur de validation réelles). **Succès sans
surprise.**

### Étape 2 — `Sale`

`@ManyToOne` vers `Site` (pattern connu), `<column unique="true">` sur
`code` (pattern connu). Aucune méthode de domaine à préserver. Testé avec
`SaleServiceIntegrationTest` (3 tests). **Succès sans surprise.**

### Étape 3 — `SaleLine`

`@ManyToOne` vers `Sale` (même module) et `Article` (déjà résolu). Rien de
nouveau techniquement. **Succès sans surprise.**

### Étape 4 — `CustomerOrder`

`@ManyToOne` vers `Site`, `@Enumerated(STRING)` sur `status`,
`<column unique="true">` sur `code` — tous des patterns déjà connus.
Aucune méthode d'invariant n'était annotée JPA : le retrait des
annotations de champ ne les touche pas, confirmé par lecture directe avant
modification et par le golden master (§1). Testé avec
`CustomerOrderServiceIntegrationTest` (5 tests, cycle de vie complet
réservation → préparation → expédition → livraison contre un vrai stock).
**Succès sans surprise.**

### Étape 5 — `CustomerOrderLine`

`@ManyToOne` vers `CustomerOrder` (même module) et `Article` (déjà
résolu). Rien de nouveau techniquement. **Succès sans surprise.**

Les 5 étapes ont été validées cumulativement à chaque étape en relançant
les tests d'intégration déjà couverts, avant de passer à la suivante.

## 3. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master `CustomerOrderTest` vert avant ET après, sans modification d'assertion | **Vérifié** — 21/21 avant (0.088 s), 21/21 après (0.072 s), fichier non retouché entre les deux runs. |
| Violations JPA disparues pour les 5 entités sales uniquement | **Vérifié** — 0 occurrence de `sales.domain.model` dans le rapport de violation post-fix. Total passé de 92 à **32** (60 violations résolues). |
| `transfers` (2 entités) inchangé | **Vérifié** — 32 violations restantes, toutes sur `transfers.{StockTransfer,StockTransferLine}` ; `organization`/`catalog`/`identity`/`inventory`/`purchasing`/`sales` tous à 0. |
| Tous les tests existants verts sans modification d'assertion | **Vérifié** — `./mvnw clean verify` : 194 tests, 1 seul échec (la règle ArchUnit elle-même, attendue, confinée à `transfers`), 0 régression. |
| `ArchitectureRulesTest`/`ModularityTests` exécutés avant de proposer le diff | **Vérifié** — `ModularityTests` 2/2 vert ; `ArchitectureRulesTest` 7/8 vert. |
| Zéro fichier touché hors `sales/` et `META-INF/orm.xml` | **Vérifié** — `git status --short` : les 5 fichiers d'entité, `orm.xml`, plus le nouveau fichier de test, rien d'autre. |

## 4. Fichiers modifiés

**Production** :
- `sales/domain/model/Customer.java`
- `sales/domain/model/CustomerOrder.java`
- `sales/domain/model/CustomerOrderLine.java`
- `sales/domain/model/Sale.java`
- `sales/domain/model/SaleLine.java`
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété +
  en-tête documentaire mis à jour)

**Test** (nouveau) :
- `sales/domain/model/CustomerOrderTest.java` — 21 tests, golden master.

**Docs** :
- `docs/migration-notes.md` — 19/21 entités résolues (était 14/21), 2
  restantes.

## 5. Ce qui a été confirmé pour la suite

Module le plus gros en nombre d'entités (5) mais le plus simple en réalité
techniquement : une seule entité sur cinq portait des invariants non
triviaux, aucun `@Version`, aucune contrainte composite, aucun
consommateur externe. Confirmation supplémentaire que le critère
déterminant pour la nécessité technique d'`orm.xml` (référencée en
`@ManyToOne`/`@OneToOne` par un autre module) reste indépendant du nombre
d'entités ou de leur complexité métier — ces deux facteurs sont
orthogonaux, comme déjà noté pour `identity` (aucun consommateur, 3
entités simples) vs `inventory` (aucun consommateur, invariants réels).

Écart méthodologique important à retenir pour `transfers`, dernier module :
ne pas présumer que `StockTransfer` a des invariants même si son nom
suggère un workflow comparable à `CustomerOrder`/`PurchaseOrder` — à
vérifier par lecture directe, comme fait ici pour `Sale`.

## 6. Zones d'ombre / décisions prises en cours de route

Aucune divergence non documentée au-delà de l'écart signalé au §1
(`Sale` sans invariant, contrairement à l'hypothèse de départ) et du cas
`numTel`/`num_tel` sur `Customer` signalé au §0.3, qui n'est pas une
anomalie mais une répétition confirmée du mécanisme déjà rencontré sur
`Supplier`.

## 7. Prochaine étape

Module `sales` terminé et vérifié vert sur son périmètre : 19 des 21
entités désormais résolues, 2 restantes
(`transfers.{StockTransfer,StockTransferLine}`), dernier module de cette
phase. **N'enchaîne pas sur `transfers` sans feu vert explicite.**
