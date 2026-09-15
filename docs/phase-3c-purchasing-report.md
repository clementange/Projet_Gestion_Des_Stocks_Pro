# Phase 3c — module `purchasing` — Rapport de sortie d'incrément

Cinquième module traité pour la séparation domaine/persistance (`Supplier`,
`PurchaseOrder`, `PurchaseOrderLine`), deuxième avec de vrais invariants
métier après `inventory`. Même règle : un seul module par incrément, arrêt
et rapport avant le suivant.

## 0. Étape 0 — golden master des invariants de `PurchaseOrder` et `PurchaseOrderLine`

Conformément à la méthode actée à l'issue de l'incrément `inventory`
("à répéter pour tout module restant portant une méthode de domaine non
triviale"), deux tests JUnit5 purs écrits et vérifiés avant tout
changement de mapping :

- **`purchasing/domain/model/PurchaseOrderTest`** (13 méthodes) — couvre
  `validate()` (nominal depuis BROUILLON, rejet depuis VALIDEE/RECUE/
  ANNULEE avec message exact), `cancel()` (nominal depuis BROUILLON/
  VALIDEE, rejet depuis RECUE/ANNULEE avec message exact),
  `requireReceivable()` (nominal depuis VALIDEE, rejet depuis les 3 autres
  états avec message exact), `markFullyReceived()`.
- **`purchasing/domain/model/PurchaseOrderLineTest`** (9 méthodes) —
  couvre `getQuantiteRestanteARecevoir()`/`isFullyReceived()` (y compris
  sur-réception), `receive()` (nominal, accumulation partielle, quantité
  ≤ 0 ou `null` rejetée, dépassement du restant rejeté avec message exact,
  cas limite exactement égal au restant accepté).

Écart constaté vs le pattern établi (identique à `inventory.Stock`) : ni
`PurchaseOrder` ni `PurchaseOrderLine` ne portent de `@Builder` Lombok —
instanciation via `new ...()` + setters.

- **Avant** tout changement de mapping : **13/13 et 9/9 verts**
  (0.025 s / 0.058 s).
- **Après** le décrochage domaine/JPA : **13/13 et 9/9 verts, identiques**
  (0.025 s / 0.058 s), 0 assertion modifiée.

## 1. Vérifications préalables

- **Consommateurs externes** : recherche exhaustive de
  `purchasing.domain.model.{Supplier,PurchaseOrder,PurchaseOrderLine}` en
  dehors de `purchasing/`. Un seul fichier externe importe quelque chose
  du package `purchasing.domain.model` :
  `services/impl/CommandeFournisseurServiceImpl.java` — mais uniquement
  l'**enum** `PurchaseOrderStatus`, référencé au travers de la couche
  application/DTO (`PurchaseOrderService`, `PurchaseOrderDto`), jamais une
  des trois classes de domaine elles-mêmes, et jamais via une relation JPA
  (`@ManyToOne`/`@OneToOne`). Contrairement à `organization.Site`/
  `catalog.Article`, `orm.xml` n'est donc pas ici une nécessité technique,
  mais un choix de cohérence avec `inventory` (décision déjà actée).
- **Colonnes physiques** — vérifiées dans `V1__initial_schema.sql`
  (`purchase_order`, `purchase_order_line`) et
  `V5__customer_supplier_and_organization_contact_fields.sql`
  (`supplier`, migration plus tardive que `V1`) :
  - `purchase_order` : `code` NOT NULL + `UNIQUE` (contrainte nommée
    `uklyhuui3e3rh2a6itktx3rwrpe`), `supplier_id`/`site_id`/`order_date`/
    `status` NOT NULL, `CHECK` sur les valeurs de `status`.
  - `purchase_order_line` : `prix_unitaire`/`quantite_commandee`/
    `quantite_recue`/`article_id`/`purchase_order_id` NOT NULL.
  - `supplier` : toutes colonnes nullables sauf la clé primaire ; colonne
    physique **`num_tel`** (avec underscore).
- **Écart inédit trouvé** : `Supplier.numTel` porte l'annotation
  `@Column(name = "numTel")` (sans underscore, camelCase) alors que la
  colonne physique réelle est `num_tel`. Vérifié que ce n'est pas une
  incohérence : Spring Boot applique par défaut `SpringPhysicalNamingStrategy`,
  qui convertit tout identifiant logique camelCase en snake_case — y
  compris les noms passés explicitement à `@Column(name=...)`, pas
  seulement les noms implicites. Donc `numTel` → `num_tel` déjà
  aujourd'hui, sans rapport avec ce changement. Décision : l'XML reprend
  le même nom littéral `numTel` (pas `num_tel`), pour rester une
  traduction 1:1 de l'annotation qu'il remplace, comme fait pour
  `organization.Organization.taxCode` (qui lui portait déjà `tax_code`
  explicite dans l'annotation Java). Confirmé correct par
  `ddl-auto=validate` au démarrage du contexte (échec bloquant sinon) et
  par `SupplierServiceImplTest` qui persiste réellement un `Supplier`.
- **Invariants métier** : confirmés réels sur `PurchaseOrder`
  (`validate`/`cancel`/`requireReceivable`/`markFullyReceived`) et
  `PurchaseOrderLine` (`receive`/`getQuantiteRestanteARecevoir`/
  `isFullyReceived`). `Supplier` reste un `@Data` pur, sans invariant.

## 2. Étapes : `Supplier` (spike) → `PurchaseOrder` → `PurchaseOrderLine`

Ordre choisi par analogie avec `identity` (mapper l'entité référencée
avant celle qui la référence) : `Supplier` d'abord (le plus simple, aucun
invariant, référencé seulement par `Long supplierId`, pas par une relation
JPA — donc en réalité indépendant de l'ordre), puis `PurchaseOrder` (a un
`@ManyToOne` vers `organization.Site`, déjà résolu), puis
`PurchaseOrderLine` (a un `@ManyToOne` vers `PurchaseOrder`, dans le même
module, et vers `catalog.Article`, déjà résolu).

### Étape 1 — `Supplier` (spike)

Champs scalaires + `@Embedded Adresse` (déjà un pattern connu depuis
`organization.Organization`/`Site`, embeddable partagé non retouché).
Seule nouveauté : le cas `numTel`/`num_tel` documenté au §1. Testé avec
`SupplierServiceImplTest` (4 tests, sauvegarde/suppression/erreur de
validation réelles contre la base). **Succès sans surprise, sans
ajustement.**

### Étape 2 — `PurchaseOrder`

`@ManyToOne` vers `Site` (pattern connu), `@Enumerated(STRING)` sur
`status` (pattern connu), `<column unique="true">` sur `code` (pattern
connu depuis `Site.code`/`Permission.code`). Aucune méthode d'invariant
n'était annotée JPA : le retrait des annotations de champ ne les touche
pas, confirmé par lecture directe avant modification et par le golden
master (§0). **Succès sans surprise.**

### Étape 3 — `PurchaseOrderLine`

`@ManyToOne` vers `PurchaseOrder` (même module — première relation
intra-module de ce type dans `orm.xml`, techniquement identique à
`identity.Role.permissions`→`Permission` en ce que les deux bouts sont
dans le même module, mais ici via `@ManyToOne` simple, pas
`@ManyToMany`) et vers `Article` (pattern connu). Rien de nouveau
techniquement. **Succès sans surprise.**

## 3. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master `PurchaseOrderTest`/`PurchaseOrderLineTest` verts avant ET après, sans modification d'assertion | **Vérifié** — 13/13 et 9/9 avant, 13/13 et 9/9 après, fichiers non retouchés entre les deux runs. |
| Violations JPA disparues pour `Supplier`/`PurchaseOrder`/`PurchaseOrderLine` uniquement | **Vérifié** — 0 occurrence de `purchasing.domain.model` dans le rapport de violation post-fix. Total passé de 129 à **92** (37 violations résolues). |
| Les 8 agrégats restants inchangés | **Vérifié** — 92 violations restantes, réparties uniquement sur `sales` (5 entités) et `transfers` (2 entités) ; `organization`/`catalog`/`identity`/`inventory`/`purchasing` tous à 0. |
| Tous les tests existants verts sans modification d'assertion | **Vérifié** — `./mvnw clean verify` : 173 tests, 1 seul échec (la règle ArchUnit elle-même, attendue et hors périmètre), 0 régression. |
| `ArchitectureRulesTest`/`ModularityTests` exécutés avant de proposer le diff | **Vérifié** — `ModularityTests` 2/2 vert ; `ArchitectureRulesTest` 7/8 vert, le seul échec restant confiné à `sales`/`transfers`. |
| Zéro fichier touché hors `purchasing/` et `META-INF/orm.xml` | **Vérifié** — `git status --short` : `Supplier.java`, `PurchaseOrder.java`, `PurchaseOrderLine.java`, `orm.xml`, plus les 2 nouveaux fichiers de test, rien d'autre. |

## 4. Fichiers modifiés

**Production** :
- `purchasing/domain/model/Supplier.java`
- `purchasing/domain/model/PurchaseOrder.java`
- `purchasing/domain/model/PurchaseOrderLine.java`
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété +
  en-tête documentaire mis à jour)

**Test** (nouveaux) :
- `purchasing/domain/model/PurchaseOrderTest.java` — 13 tests, golden
  master.
- `purchasing/domain/model/PurchaseOrderLineTest.java` — 9 tests, golden
  master.

**Docs** :
- `docs/migration-notes.md` — 14/21 entités résolues (était 11/21), 7
  restantes.

## 5. Ce qui a été confirmé pour la suite

Deuxième module consécutif avec invariants métier réels, méthode étape 0
répétée avec succès (aucune itération de correction nécessaire, comme pour
`inventory`). Nouveau cas rencontré : un nom de colonne littéral dans
`@Column(name=...)` qui diffère du nom physique réel en base à cause de la
transformation camelCase→snake_case de `SpringPhysicalNamingStrategy` —
confirmé que la traduction XML doit reprendre le **même nom littéral que
l'annotation d'origine**, jamais le nom physique post-transformation, et
que `ddl-auto=validate` est le filet qui détecterait immédiatement une
erreur sur ce point précis.

Il reste 7 entités dans 2 modules, tous deux avec invariants métier réels
(`sales.Sale`, `sales.CustomerOrder`, etc. ; `transfers.StockTransfer`) :
l'étape 0 golden-master reste obligatoire pour chacun.

## 6. Zones d'ombre / décisions prises en cours de route

Aucune divergence non documentée au-delà du cas `numTel`/`num_tel`
signalé au §1, qui n'est pas une anomalie mais un mécanisme déjà en place
avant cet incrément — vérifié, pas corrigé (rien à corriger).

## 7. Prochaine étape

Module `purchasing` terminé et vérifié vert sur son périmètre : 14 des 21
entités désormais résolues, 7 restantes
(`sales.{Customer,CustomerOrder,CustomerOrderLine,Sale,SaleLine}`,
`transfers.{StockTransfer,StockTransferLine}`). **N'enchaîne pas sur un
autre module sans feu vert explicite.**
