# Finition post-Phase-3c — Incrément A — Rapport

Sujet : le garde-fou de suppression d'`ArticleServiceImpl.delete()`
(catalog) qui catche `DataIntegrityViolationException` pour en déduire
"article référencé ailleurs" — vérification demandée : peut-on le rendre
aussi explicite que les garde-fous applicatifs habituels du projet
(pré-vérification via facade avant la tentative de suppression) ?

**Conclusion : laisser tel quel. Le catch n'est pas un raccourci — c'est,
après vérification, l'unique approche qui respecte à la fois la frontière
de modules établie en Phase 3a et l'interdiction CLAUDE.md sur le legacy
plat.** Un seul changement apporté : le commentaire au-dessus de
`delete()` était factuellement inexact (il laissait croire à 4
vérifications explicites côté legacy plat, il n'y en a que 3) — corrigé et
complété pour documenter précisément le périmètre du catch.

## 1. Caractérisation complète du périmètre du catch

Recherche exhaustive de toutes les contraintes FK référençant
`article(id)` dans `V1__initial_schema.sql` — **10 constraintes, 10
tables**, aucune ailleurs (aucune autre migration ne touche `article`) :

| Table | Colonne FK | Vérifiée explicitement dans `delete()` ? |
|---|---|---|
| `lignecommandeclient` (legacy) | `idarticle` | **Oui** — `commandeClientRepository.findAllByArticleId` |
| `lignecommandefournisseur` (legacy) | `idarticle` | **Oui** — `commandeFournisseurRepository.findAllByArticleId` |
| `lignevente` (legacy) | `idarticle` | **Oui** — `venteRepository.findAllByArticleId` |
| `mvtstk` (legacy) | `idarticle` | **Non** — catch uniquement |
| `stock` (inventory) | `article_id` | Non — catch uniquement |
| `stock_movement` (inventory) | `article_id` | Non — catch uniquement |
| `purchase_order_line` (purchasing) | `article_id` | Non — catch uniquement |
| `customer_order_line` (sales) | `article_id` | Non — catch uniquement |
| `sale_line` (sales) | `article_id` | Non — catch uniquement |
| `stock_transfer_line` (transfers) | `article_id` | Non — catch uniquement |

**Écart trouvé vs le contexte de la demande** : l'instruction évoquait "la
partie legacy de cette même méthode [qui fait déjà ça] pour les 4 tables
plates". En réalité, seules **3** des 4 tables legacy plates sont
vérifiées explicitement (`lignecommandeclient`, `lignecommandefournisseur`,
`lignevente`) ; la 4e (`mvtstk`) ne l'a jamais été et dépend, comme les 6
tables des modules neufs, entièrement du catch. Signalé plutôt que
silencieusement aligné sur l'hypothèse de départ.

Confirmé également : `article` elle-même ne porte aucune contrainte
`CHECK` ni aucun trigger (`grep -i TRIGGER` sur toutes les migrations : 0
résultat). Un `DELETE FROM article WHERE id = ?` ne peut donc échouer en
`DataIntegrityViolationException` que par l'une de ces 10 FK — **le
périmètre du catch est entièrement caractérisé, ce n'est pas une
supposition**. Aucun risque de faux positif identifié : il n'existe pas
d'autre contrainte d'intégrité qu'une suppression par clé primaire sur
cette table pourrait déclencher.

## 2. Pourquoi les 6 tables des modules neufs ne peuvent pas être pré-vérifiées explicitement

`stock`, `stock_movement` (inventory), `purchase_order_line` (purchasing),
`customer_order_line`, `sale_line` (sales), `stock_transfer_line`
(transfers) référencent toutes `catalog.Article` via un `@ManyToOne` —
c'est-à-dire que **ces 4 modules dépendent déjà de `catalog`**. Une
pré-vérification depuis `catalog` (appel à
`InventoryFacade`/`PurchaseOrderService`/`CustomerOrderService`/
`SaleService`/`StockTransferService` avant la suppression) obligerait
`catalog` à dépendre en retour de ces 4 modules — recréant exactement le
cycle que la Phase 3a a éliminé (`docs/phase-3a-report.md`), et cette fois
sur 4 modules au lieu de 2 (`sales`/`purchasing` uniquement en Phase 3a).
`ModularityTests`/`ArchitectureRulesTest` détecteraient ce cycle
immédiatement s'il était introduit.

**Verdict : bloqué architecturalement, pas seulement risqué.**

## 3. Pourquoi `mvtstk` ne peut pas non plus être pré-vérifiée explicitement

Investigation menée avant de conclure :

- Il n'existe **aucun `MvtStkRepository`** dans le code actuel — les 3
  vérifications legacy existantes s'appuient sur des repositories Spring
  Data déjà présents (`LigneCommandeClientRepository`,
  `LigneCommandeFournisseurRepository`, `LigneVenteRepository`) ; il n'y a
  pas d'équivalent pour `MvtStk`.
- En créer un consisterait à ajouter un nouveau fichier dans
  `repository/` à la racine du package — exactement l'un des répertoires
  cités nommément par CLAUDE.md sous "Aucune nouvelle fonctionnalité dans
  le legacy plat (model/, services/, services/impl/, dto/, controller/,
  repository/ à la racine du package)". Zéro tolérance sur cette règle.
- Une requête native directe (`JdbcTemplate`/`EntityManager`, sans
  repository dédié) contournerait la lettre de la règle mais pas son
  esprit — coupler `catalog` au schéma physique d'une table legacy gelée
  est un pattern plus fragile que l'existant, pas une amélioration.
- Vérifié par lecture de `MvtStkServiceImpl` (Phase 22, déjà git-loggée) :
  cette classe est **entièrement re-backée par
  `inventory.Stock`/`StockMovement`** — elle n'écrit plus jamais dans la
  table `mvtstk`, ne dépend d'aucun repository sur cette table. Le FK de
  `mvtstk` vers `article` ne protège donc plus que des lignes
  historiques, antérieures à la Phase 22 ; son ensemble ne croît plus.
  Cela réduit encore la valeur d'un correctif pour un coût (violation de
  CLAUDE.md) qui resterait, lui, inchangé.

**Verdict : bloqué par CLAUDE.md, pas seulement risqué — et de valeur
marginale décroissante vu le gel de la table depuis la Phase 22.**

## 4. Le catch est-il déjà testé ?

Oui, empiriquement, pour au moins une des 6 tables modules-neufs :
`ArticleHistoryLegacyEndpointsTest.deleteArticleUsedInCommandeFournisseurIsRejected`
crée une commande fournisseur via le contrat HTTP legacy
(`/commandesfournisseurs/create`, re-backé par
`purchasing.PurchaseOrder`/`PurchaseOrderLine` depuis la Phase 21), ce qui
insère une ligne dans `purchase_order_line`, puis vérifie que la
suppression de l'article échoue avec `400 ARTICLE_ALREADY_IN_USE`. Ce test
passe et exerce réellement le chemin catch → `InvalidOperationException`,
pas seulement les 3 vérifications explicites. Confirmé en le relançant
(1/1 vert) dans cet incrément.

## 5. Changement apporté

**Aucun changement de logique.** Seul le commentaire au-dessus de
`delete()` dans `ArticleServiceImpl.java` a été corrigé et complété pour :

- Rectifier l'inexactitude ("4 tables plates vérifiées" → en réalité 3,
  `mvtstk` non couverte explicitement).
- Énumérer les 10 contraintes FK et leur statut (3 vérifiées
  explicitement, 7 via le catch).
- Documenter les deux raisons distinctes qui bloquent l'extension du
  pattern explicite (cycle de modules pour les 6 tables neuves ;
  CLAUDE.md pour `mvtstk`).
- Noter que le catch est précis (périmètre entièrement caractérisé, pas
  de risque de faux positif) plutôt que de le laisser paraître comme un
  compromis approximatif.

Vérifié après modification : `ArchitectureRulesTest` (6/6),
`ModularityTests` (2/2), `ArticleServiceImplTest` (3/3),
`ArticleHistoryLegacyEndpointsTest` (5/5) — tous verts, comme avant (le
changement est un commentaire, aucune régression possible mais vérifié
par principe).

## 6. Fichiers modifiés

- `catalog/application/impl/ArticleServiceImpl.java` — commentaire
  uniquement, aucune ligne de code exécutable modifiée.

## 7. Prochaine étape

Incrément A terminé. J'attends le feu vert avant l'incrément B.
