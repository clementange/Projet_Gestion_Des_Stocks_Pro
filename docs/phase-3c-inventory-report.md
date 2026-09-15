# Phase 3c — module `inventory` — Rapport de sortie d'incrément

Quatrième module traité pour la séparation domaine/persistance (`Stock`,
`StockMovement`), premier à porter de vrais invariants métier
(`issue`/`receive`/`reserve`/`releaseReservation`/`correct`) et le premier
`@Version` (verrouillage optimiste) de tout le schéma. Même règle : un seul
module par incrément, arrêt et rapport avant le suivant.

## 0. Étape 0 (nouvelle, obligatoire) — golden master des invariants de `Stock`

Contrairement à `organization`/`catalog`/`identity`, ces invariants
n'étaient jusqu'ici exercés qu'indirectement, via des tests d'intégration
complets (`InventoryFacadeIntegrationTest`,
`CrossModuleConcurrencyIntegrationTest`) — un filet réel mais indirect.
Écriture de `inventory/domain/model/StockTest.java`, test JUnit5 pur
(aucun `@SpringBootTest`, aucun contexte Spring), 22 méthodes couvrant :

- `getQuantiteDisponible()` (cohérence physique − réservée, y compris après
  `reserve`+`releaseReservation` partiel) ;
- `receive()` nominal, quantité nulle, négative, `null` ;
- `issue()` nominal, dépassement du disponible (avec message exact), prise
  en compte de la réservation dans le disponible, quantité ≤ 0 ou `null` ;
- `reserve()` nominal, dépassement du disponible, quantité ≤ 0 ;
- `releaseReservation()` nominal, libération supérieure à la réservation
  réelle (avec message exact), quantité ≤ 0 ;
- `correct()` delta positif, delta négatif nominal, delta négatif
  dépassant le physique (avec message exact), delta négatif exactement
  égal au physique (cas limite accepté), delta `null` ou zéro (rejetés).

**Écart constaté vs l'hypothèse de départ** : `Stock` ne porte pas de
`@Builder` Lombok (seulement `@Data`) — instanciation via `new Stock()` +
setters (helper privé `stockOf(physique, reservee)`), pas via un builder
qui n'existe pas. Documenté dans le javadoc du test plutôt que de faire
apparaître un builder artificiellement.

- **Avant** tout changement de mapping : **22/22 verts, 0.088 s.**
- **Après** le décrochage domaine/JPA (ci-dessous) : **22/22 verts,
  0.091 s, zéro assertion modifiée** — confirmation directe, indépendante
  des tests d'intégration, que le comportement métier n'a pas bougé.

## 1. Vérifications préalables

- **Consommateurs externes** : recherche exhaustive de
  `inventory.domain.model.{Stock,StockMovement}` en dehors de `inventory/`
  — **0 résultat pour les deux**. Contrairement à
  `organization.Site`/`catalog.Article`, ni `Stock` ni `StockMovement` ne
  sont référencées en `@ManyToOne`/`@OneToOne` direct par un autre module
  ou par le legacy plat. `orm.xml` n'est donc pas ici une nécessité
  technique, mais un choix explicite de cohérence avec les 3 modules
  précédents (décision utilisateur confirmée avant d'écrire le code).
- **Colonnes physiques** — vérifiées dans `V1__initial_schema.sql` :
  table `stock` (`article_id`/`site_id` NOT NULL, `quantite_physique`/
  `quantite_reservee` NOT NULL, `seuil_alerte` nullable, `version`,
  contrainte `UNIQUE (article_id, site_id)` via un `ALTER TABLE` nommé par
  hash) ; table `stock_movement` (`article_id`/`site_id`/`date_mvt`/
  `quantite`/`type`/`source` NOT NULL, `reference`/`user_id` nullables).
  Toutes identiques aux noms de champs actuels.
- **`@Version`** — confirmé via `V2__drop_stray_optimistic_lock_columns.sql`
  (son propre commentaire SQL) que `Stock` est **la seule entité de tout
  le schéma** à porter légitimement un numéro de version. Point de
  vigilance dédié pour cet incrément, jamais rencontré dans les 3
  précédents.
- **Invariants métier** : confirmés réels sur `Stock` uniquement
  (`StockMovement` reste un `@Data` pur, sans logique).

## 2. Étapes : `StockMovement` (spike) → `Stock` (extension)

### Étape 1 — `StockMovement` (spike, la plus simple)

Deux `@ManyToOne` (`Article`, `Site` — déjà connus du pattern
`organization`/`catalog`), un `@Enumerated(STRING)` par champ (`type`,
`source` — déjà connu du pattern `Site.type`/`UserRoleAssignment.scopeType`),
le reste en `<basic>`. Aucune logique métier sur cette classe — changement
mécanique. **Succès sans surprise.**

### Étape 2 — `Stock` (extension, deux nouveautés)

1. **`<version>`** — première apparition dans `orm.xml` :
   ```xml
   <version name="version"><column name="version"/></version>
   ```
2. **Contrainte d'unicité composite** — première apparition d'un
   `<unique-constraint>` multi-colonnes au niveau `<table>` (les
   `unique="true"` précédents portaient tous sur une seule colonne) :
   ```xml
   <table name="stock">
     <unique-constraint>
       <column-name>article_id</column-name>
       <column-name>site_id</column-name>
     </unique-constraint>
   </table>
   ```

Les méthodes d'invariant (`issue`, `receive`, `reserve`,
`releaseReservation`, `correct`, `getQuantiteDisponible`, les trois
`require*` privées) ne portaient aucune annotation JPA : le retrait des
annotations de champ ne les touche pas, confirmé par lecture directe du
fichier avant modification et par le golden master (§0).

Les deux nouveautés sont validées par deux canaux indépendants :
`ddl-auto=validate` passe au démarrage du contexte (la forme XML déclarée
correspond exactement au schéma réel en base) et
`InventoryFacadeIntegrationTest` (qui persiste et fait entrer en conflit
de version un vrai `Stock`) passe sans ajustement. **Succès sans surprise,
sans itération de correction.**

## 3. Vérification dédiée — `CrossModuleConcurrencyIntegrationTest`

Conformément à l'instruction explicite de ne pas se reposer uniquement sur
`./mvnw clean verify` pour ce point : lecture complète du test pour en
comprendre le mécanisme, puis exécution **individuelle et répétée** :

```
./mvnw test -Dtest=CrossModuleConcurrencyIntegrationTest
```

exécuté **trois fois séparément**, trois fois vert (1/1 à chaque run).
Une seule exécution verte d'un test de condition de course ne suffit pas à
exclure un entrelacement de threads "chanceux" qui ne déclencherait jamais
le conflit ; trois exécutions indépendantes toutes vertes donnent une
garantie bien plus solide que la garantie "jamais de survente" tient
toujours sous concurrence réelle avec le `@Version` désormais exprimé en
XML plutôt qu'en annotation. Le mécanisme reste inchangé côté
`InventoryFacadeImpl` (traduction de
`ObjectOptimisticLockingFailureException` en
`InvalidOperationException(STOCK_CONCURRENT_MODIFICATION)`) : seul le
support déclaratif du verrou optimiste a changé de forme, pas son
fonctionnement runtime — cohérent avec le fait que Hibernate ne fait
aucune distinction entre `@Version` annoté et `<version>` XML une fois le
métamodèle construit.

## 4. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master `StockTest` vert avant ET après, sans modification d'assertion | **Vérifié** — 22/22 avant (0.088 s), 22/22 après (0.091 s), fichier de test non retouché entre les deux runs. |
| Violations JPA disparues pour `Stock`/`StockMovement` uniquement | **Vérifié** — 0 occurrence de `inventory.domain.model.{Stock,StockMovement}` dans le rapport de violation post-fix. Total passé de 169 à **129** (40 violations, 2 entités × ~20 annotations chacune). |
| Les 10 agrégats restants inchangés | **Vérifié** — 129 violations restantes, aucune sur `organization`/`catalog`/`identity`/`inventory` (tous à 0). |
| Tous les tests existants verts sans modification d'assertion | **Vérifié** — `./mvnw clean verify` : 151 tests, 1 seul échec (la règle ArchUnit `domain_must_not_depend_on_jakarta_persistence` elle-même, attendue et hors périmètre), 0 régression. |
| `CrossModuleConcurrencyIntegrationTest` re-vérifié individuellement, garantie commentée | **Vérifié** — 3 exécutions individuelles séparées, 3/3 vertes (1/1 chacune). Voir §3. |
| Zéro fichier touché hors `inventory/` et `META-INF/orm.xml` | **Vérifié** — `git status --short` : `Stock.java`, `StockMovement.java`, `orm.xml`, `StockTest.java` (nouveau) uniquement. Sans surprise ici puisque, contrairement à `Site`/`Article`, `Stock`/`StockMovement` n'ont aucun consommateur externe à risquer de toucher. |

## 5. Fichiers modifiés

**Production** :
- `inventory/domain/model/Stock.java` (annotations retirées, invariants
  intacts)
- `inventory/domain/model/StockMovement.java` (annotations retirées)
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété + en-tête
  documentaire mis à jour)

**Test** (nouveau) :
- `inventory/domain/model/StockTest.java` — 22 tests, golden master de
  l'étape 0, écrit et vérifié avant tout changement de mapping.

**Docs** :
- `docs/migration-notes.md` — 11/21 entités résolues (était 9/21), 10
  restantes, `<version>` et `<unique-constraint>` composite actés comme
  techniques prouvées pour les modules suivants avec invariants réels.

## 6. Ce qui a été confirmé pour la suite

Premier module avec invariants métier réels, premier `@Version`, premier
`<unique-constraint>` composite — les trois franchis sans itération de
correction. Le pattern `orm.xml` couvre maintenant l'intégralité du
vocabulaire JPA rencontré dans ce projet à ce jour : `@Basic`,
`@ManyToOne`/`@OneToOne`/`@OneToMany`/`@ManyToMany` (simples et via table
de jointure), `@Embedded`, `@Enumerated(STRING)`, `@Version`, contrainte
d'unicité simple et composite.

Point méthodologique retenu pour les modules à invariants restants
(`purchasing.PurchaseOrder`, `sales.CustomerOrder`, `transfers.StockTransfer`,
etc.) : l'étape 0 (golden master pur JUnit AVANT tout changement de
mapping) doit devenir systématique dès qu'une entité porte une méthode de
domaine non triviale, pas seulement pour `inventory`. C'est elle, pas les
tests d'intégration existants, qui a apporté la preuve directe et
indépendante que le comportement métier n'a pas bougé.

## 7. Zones d'ombre / décisions prises en cours de route

Aucune divergence non documentée. Le seul écart par rapport aux hypothèses
de départ (absence de `@Builder` Lombok sur `Stock`) est signalé au §0 et
dans le javadoc du test, plutôt que silencieusement absorbé.

## 8. Prochaine étape

Module `inventory` terminé et vérifié vert sur son périmètre : 11 des 21
entités désormais résolues, 10 restantes
(`purchasing.{PurchaseOrder,PurchaseOrderLine,Supplier}`,
`sales.{Customer,CustomerOrder,CustomerOrderLine,Sale,SaleLine}`,
`transfers.{StockTransfer,StockTransferLine}`). **N'enchaîne pas sur un
autre module sans feu vert explicite.**
