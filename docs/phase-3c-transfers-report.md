# Phase 3c — module `transfers` — Rapport de sortie d'incrément

Septième et dernier module traité pour la séparation domaine/persistance
(`StockTransfer`, `StockTransferLine`). Clôt la Phase 3c : les 21 entités
de domaine des 8 modules "neufs" identifiées en Phase 2 comme directement
annotées JPA sont désormais toutes résolues.

## 0. Vérifications préalables

Le contexte fourni par l'utilisateur (machine à états sur `StockTransfer`,
`StockTransferLine` en `@Data` pur, zéro consommateur externe) a été
revérifié indépendamment plutôt que pris pour acquis, plus les 3 points de
vigilance explicitement demandés.

### Consommateurs externes

Recherche exhaustive de `transfers.domain.model.{StockTransfer,
StockTransferLine}` en dehors de `transfers/` — **0 résultat pour les
deux**, y compris en légacy plat (contrairement à `purchasing`/`sales`, il
n'y a même pas d'import d'enum externe ici : aucun service legacy
n'expose de contrat `StockTransfer`). Confirmé.

### Colonnes physiques (point de vigilance n°1)

Vérifiées dans `V1__initial_schema.sql`, seule migration touchant ces
tables (`grep -rl stock_transfer db/migration` ne retourne que `V1`) :

- `stock_transfer` : `code` NOT NULL + `UNIQUE` (contrainte
  `ukigysahcicler5iqllvk4hq6x6`), `origin_site_id`/`destination_site_id`/
  `status`/`requested_by_user_id`/`request_date` NOT NULL,
  `approved_by_user_id` nullable, `CHECK` sur les valeurs de `status`.
- `stock_transfer_line` : `quantite`/`article_id`/`stock_transfer_id`
  NOT NULL.

Toutes les colonnes correspondent **exactement** au nom de champ
transformé par `SpringPhysicalNamingStrategy` (`originSite`→`origin_site_id`
via `@JoinColumn` déjà explicite, `requestedByUserId`→`requested_by_user_id`,
`approvedByUserId`→`approved_by_user_id`, `requestDate`→`request_date`) —
**aucun troisième cas `numTel`/`num_tel`** : contrairement à `Supplier`/
`Customer`, aucune colonne de ce module n'a de nom littéral divergent du
nom physique. Vérifié colonne par colonne, pas supposé par analogie.

### `@Version` / contrainte composite (point de vigilance n°2)

Recherche de `@Version` et `uniqueConstraints` dans les 2 fichiers de
domaine `transfers` — **aucune occurrence**. Un seul `UNIQUE (code)`
simple sur `stock_transfer` (pattern déjà connu). Confirmé par recherche
explicite, pas par supposition : ni `@Version` ni contrainte composite
dans ce module.

### Ordre de dépendance (point de vigilance n°3)

`StockTransferLine.stockTransfer` est un `@ManyToOne` vers `StockTransfer`
(même module) ; `StockTransfer` n'est référencée par aucune autre relation
dans ce module. Le sens de dépendance est donc bien
`StockTransferLine` → `StockTransfer`, confirmant l'ordre choisi :
**`StockTransfer` d'abord, `StockTransferLine` ensuite**.

### Invariants métier

`StockTransfer` confirmée porteuse d'une machine à états complète par
lecture directe du code (`submit`, `approve`, `requirePreparable`/
`markInPreparation`, `requireShippable`/`markShipped`,
`requireReceivable`/`markReceived`, `cancel`, plus le helper privé
`requireStatus`) — le seul écart notable vs `CustomerOrder`/`PurchaseOrder`
est que `approve(Long userId)` mute aussi `approvedByUserId`, pas
seulement `status`. `StockTransferLine` confirmée `@Data` pur, sans
méthode de domaine, par lecture directe avant d'écrire le mapping (pas
pris pour acquis malgré l'indication de l'utilisateur).

## 1. Étape 0 — golden master de `StockTransfer`

**`transfers/domain/model/StockTransferTest`** (20 méthodes) — couvre
chaque transition d'état (nominale + rejet avec message exact) sur les 7
états (`BROUILLON`, `DEMANDE`, `APPROUVE`, `EN_PREPARATION`, `EXPEDIE`,
`RECU`, `ANNULE`), plus la mémorisation de `approvedByUserId` par
`approve()`.

`StockTransfer` ne porte pas de `@Builder` Lombok (seulement `@Data`) —
instanciation via `new StockTransfer()` + setter, comme pour tous les
agrégats précédents sans builder.

- **Avant** tout changement de mapping : **20/20 verts, 0.088 s.**
- **Après** le décrochage domaine/JPA : **20/20 verts, identiques**
  (0.069 s), 0 assertion modifiée.

## 2. Étapes : `StockTransfer` → `StockTransferLine`

### Étape 1 — `StockTransfer`

Deux `@ManyToOne` vers `Site` (`originSite`/`destinationSite` — deux
relations vers la même entité cible, déjà rencontré en substance avec les
autres modules référençant `Site`, ici simplement doublé sur la même
entité), `@Enumerated(STRING)` sur `status`, `<column unique="true">` sur
`code` — tous des patterns déjà connus. Aucune méthode d'invariant
n'était annotée JPA : le retrait des annotations de champ ne les touche
pas, confirmé par lecture directe avant modification et par le golden
master (§1). Testé avec `StockTransferServiceIntegrationTest` (5 tests).
**Succès sans surprise.**

### Étape 2 — `StockTransferLine`

`@ManyToOne` vers `StockTransfer` (même module) et `Article` (déjà
résolu). Rien de nouveau techniquement. **Succès sans surprise.**

## 3. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master `StockTransferTest` vert avant ET après, sans modification d'assertion | **Vérifié** — 20/20 avant (0.088 s), 20/20 après (0.069 s), fichier non retouché entre les deux runs. |
| Violations JPA disparues pour les 2 entités transfers (total attendu : 0 sur les 21) | **Vérifié** — `ArchitectureRulesTest` **6/6 vert, 0 violation restante** sur la règle `domain_must_not_depend_on_jakarta_persistence` (elle-même passe désormais, plus de failure attendue). |
| Tous les tests existants verts | **Vérifié** — `./mvnw clean verify` : **214 tests, 0 échec, 0 erreur** (194 avant cet incrément + 20 nouveaux). |
| `ArchitectureRulesTest`/`ModularityTests` exécutés avant de proposer le diff | **Vérifié** — les deux 100% verts (`ArchitectureRulesTest` 6/6, `ModularityTests` 2/2). |
| Zéro fichier touché hors `transfers/` et `META-INF/orm.xml` | **Vérifié** — `git status --short` : `StockTransfer.java`, `StockTransferLine.java`, `orm.xml`, plus le nouveau fichier de test, rien d'autre. |

## 4. Fichiers modifiés

**Production** :
- `transfers/domain/model/StockTransfer.java`
- `transfers/domain/model/StockTransferLine.java`
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété +
  en-tête documentaire mis à jour)

**Test** (nouveau) :
- `transfers/domain/model/StockTransferTest.java` — 20 tests, golden
  master.

**Docs** :
- `docs/migration-notes.md` — 21/21 entités résolues (était 19/21), 0
  restante, section "Trouvé pendant Phase 2" §1 clôturée.

## 5. Ce qui a été confirmé pour la suite

Dernier module de la phase, aucune surprise, aucune itération de
correction. `ArchitectureRulesTest` passe désormais intégralement (6/6) :
la règle `domain_must_not_depend_on_jakarta_persistence`, qui échouait
depuis la Phase 2 avec 272 violations initiales sur les 21 entités,
n'échoue plus du tout. Les 21 entités de domaine des 8 modules "neufs"
sont toutes mappées via `META-INF/orm.xml`, sans exception, sans
divergence de comportement (chaque étape validée par golden master pur
quand des invariants existaient, ou par tests d'intégration existants
sinon).

Bilan global de la Phase 3c (7 incréments, 21 entités, 0 régression sur
l'ensemble) :

| Module | Entités | Invariants réels | Particularité technique |
|---|---|---|---|
| organization (pilote) | 4 | aucun | établit la technique orm.xml elle-même |
| catalog | 2 | aucun | `@OneToMany`/`mapped-by` bidirectionnel |
| identity | 3 | aucun | `@ManyToMany`/`@JoinTable` |
| inventory | 2 | `Stock` | `@Version`, `unique-constraint` composite |
| purchasing | 3 | `PurchaseOrder`, `PurchaseOrderLine` | cas `numTel`/`num_tel` (1er) |
| sales | 5 | `CustomerOrder` seule (`Sale` sans invariant, écart signalé) | cas `numTel`/`num_tel` (2e) |
| transfers | 2 | `StockTransfer` | aucune nouveauté technique |

## 6. Zones d'ombre / décisions prises en cours de route

Aucune divergence non documentée.

## 7. Prochaine étape

**Phase 3c terminée : 21/21 entités résolues, `ArchitectureRulesTest`
intégralement vert.** Conformément à l'instruction reçue, aucun autre
module ni aucune autre phase n'est entamé — j'attends le bilan global
demandé et la décision sur la suite (Phase 4 : extinction du legacy plat,
ou retour sur les 2 points de vigilance déjà notés en cours de route :
garde-fou FK basé sur `DataIntegrityViolationException` en Phase 3a,
absence de tests unitaires purs pré-existants sur les agrégats avant que
l'étape 0 ne les introduise).
