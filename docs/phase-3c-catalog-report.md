# Phase 3c — module `catalog` — Rapport de sortie d'incrément

Deuxième module traité pour la séparation domaine/persistance
(`Article`, `Category`), même règle qu'`organization` : un seul module par
incrément, arrêt et rapport avant le suivant. Contexte déjà vérifié par
l'utilisateur pris comme acquis pour les points qualitatifs (absence
d'invariants métier) ; toutes les affirmations factuelles précises
(consommateurs externes, colonnes physiques) revérifiées indépendamment
avant d'écrire le XML, comme demandé.

## 1. Vérifications préalables (avant d'écrire une ligne de code)

**Consommateurs externes d'`Article`** — recherche exhaustive
(`catalog.domain.model.Article` en dehors de `catalog/`) : **11 fichiers**,
plus nombreux que pour `Site` (7) en Phase 3c/organization :
- 4 modules neufs : `transfers.StockTransferLine`, `sales.SaleLine`,
  `sales.CustomerOrderLine`, `inventory.Stock`, `inventory.StockMovement`,
  `inventory.application.impl.InventoryFacadeImpl`,
  `purchasing.PurchaseOrderLine` (7 fichiers sur 4 modules :
  transfers, sales, inventory, purchasing — confirme la phrase "au moins
  inventory, purchasing, sales" de la consigne, avec transfers en plus).
- 4 entités legacy plates : `model.LigneVente`, `model.LigneCommandeClient`,
  `model.LigneCommandeFournisseur`, `model.MvtStk` — hors périmètre de la
  règle ArchUnit module/legacy (Phase 1) mais réelles références JPA
  `@ManyToOne` à préserver telles quelles quand même, puisque `orm.xml` ne
  change de toute façon rien pour elles (le FQCN d'`Article` ne bouge pas).

**Consommateurs externes de `Category`** — même recherche : **0 résultat**.
Confirme l'hypothèse de la consigne : seul `Article` la référence
(relation bidirectionnelle interne au module, `Category.articles`
`@OneToMany(mappedBy = "category")`).

**Colonnes physiques** — vérifiées dans `V1__initial_schema.sql`
(`CREATE TABLE public.article`/`public.category`) avant d'écrire le XML,
pas supposées identiques aux annotations sans recroisement : `codearticle`,
`designation`, `identreprise`, `photo`, `prixunitaireht`, `prixunitairettc`,
`tauxtva`, `idcategory`, `organization_id` pour `article` ; `code`,
`designation`, `identreprise`, `organization_id` pour `category`. **Toutes
nullable** (aucun `NOT NULL` au-delà de `id`/`creation_date` dans les deux
tables) — cohérent avec les annotations actuelles, qui ne déclarent aucun
`nullable = false`. Contrairement à `tax_code` en Phase 3c/organization,
**aucun écart camelCase/snake_case** trouvé ici : toutes les colonnes sont
déjà des mots simples en minuscules, sans transformation de nommage en jeu.

**Invariants métier** — confirmé nul sur les deux classes (comme indiqué
dans le contexte fourni) : `Article`/`Category` sont des `@Data` purs,
aucune méthode de domaine.

## 2. Étape 1 : spike sur `Category` seule

`Category` choisie en premier (un seul lien sortant vers `Organization`,
pas de fan-in externe — le plus simple des deux).

- Ajout au fichier `META-INF/orm.xml` existant (celui créé en Phase
  3c/organization) plutôt qu'un nouveau fichier séparé : Spring Boot n'a
  été vérifié capable d'auto-découvrir que le nom exact
  `META-INF/orm.xml` (Phase 3c/organization) — un fichier partagé entre
  modules reste sur ce mécanisme déjà validé, au lieu d'en introduire un
  second non testé (ex. `META-INF/orm-catalog.xml` avec
  `spring.jpa.mapping-resources`).
- `Category` étant hors du `<package>` par défaut du fichier
  (`organization.domain.model`), déclarée avec un `class` pleinement
  qualifié (`com.kfokam48.gestiondestock.catalog.domain.model.Category`) —
  point technique non rencontré en Phase 3c/organization (un seul module
  dans le fichier à l'époque), vérifié ici pour la première fois.
- Relation `@OneToMany(mappedBy = "category")` traduite en
  `<one-to-many name="articles" ... mapped-by="category"/>` — autre point
  nouveau par rapport à organization (aucune relation `mappedBy` là-bas).
- Annotations retirées de `Category.java` (Lombok, `extends AbstractEntity`,
  noms de champs inchangés).

**Résultat : succès sans surprise.** `CategoryServiceImplTest` (5 tests,
écritures réelles via `categoryService.save(...)`) passe au vert du
premier coup — confirme que (a) une entité déclarée hors du `<package>`
par défaut via `class` pleinement qualifié fonctionne dans le même
fichier, et (b) la relation `mappedBy` inverse se résout correctement.
Passage direct à l'étape 2, comme prévu (pas de surprise nécessitant un
arrêt).

## 3. Étape 2 : extension à `Article`

Ajout de l'entité `Article` au même `orm.xml`, y compris ses deux relations
`@ManyToOne` (`organization`, `category`) et ses 6 colonnes scalaires.
Annotations retirées d'`Article.java`.

**Résultat : succès, sans itération de correction.**
`ArticleServiceImplTest` (3 tests) vert. `./mvnw clean verify` complet
ensuite : 129 tests, 1 seul échec (la règle JPA elle-même, attendue), 0
régression — en particulier les tests qui exercent réellement les
relations `@ManyToOne Article` depuis d'autres modules
(`InventoryFacadeIntegrationTest`, `SaleServiceIntegrationTest`,
`PurchaseOrderServiceIntegrationTest`, `StockTransferServiceIntegrationTest`,
et les tests de caractérisation `legacy/*` qui créent des lignes de
vente/commande/mouvement référençant un article) tous verts sans
modification.

## 4. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Violations JPA disparues pour `Article`/`Category` uniquement | **Vérifié** — 0 occurrence de `catalog.domain.model.{Article,Category}` dans le rapport de violation post-fix. Total passé de 227 à **196** (31 violations, pas 45 comme l'estimation approximative de la consigne l'anticipait — `Article` a moins de colonnes/relations que les 4 entités `organization` combinées ; chiffre mesuré, pas recalculé à la main). |
| Les 15 agrégats restants inchangés | **Vérifié** — 196 violations restantes, aucune sur `catalog`, `organization` (déjà à 0), toutes les autres inchangées par rapport au rapport Phase 3c/organization. |
| Tous les tests existants verts sans modification d'assertion | **Vérifié** — 129 tests, 1 échec (attendu), 0 erreur de démarrage de contexte, 0 fichier de test modifié. |
| Zéro fichier touché hors `catalog/` et `META-INF/orm.xml` | **Vérifié** — `git diff --quiet` sur les 11 fichiers externes (7 modules neufs + 4 legacy) : tous `UNCHANGED`. |

## 5. Fichiers modifiés

**Production** :
- `catalog/domain/model/Article.java`
- `catalog/domain/model/Category.java`
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété — pas
  de nouveau fichier)

**Aucun fichier de test modifié ou ajouté** — `ArticleServiceImplTest` (3
tests) et `CategoryServiceImplTest` (5 tests), déjà existants, constituent
le test de non-régression.

**Docs** :
- `docs/migration-notes.md` — 6/21 entités résolues (était 4/21), 15
  restantes, le constat structurel confirmé une seconde fois (le critère
  déterminant est le fan-in `@ManyToOne` externe, pas une propriété du
  module) avec les deux points techniques nouveaux de cet incrément
  (entité hors `<package>` par défaut, relation `mappedBy`) actés comme
  résolus sans réserve pour la suite.

## 6. Ce qui a été confirmé pour la suite

Le pattern `orm.xml` tient sur un deuxième module sans ajustement de fond,
y compris sur deux points non testés en Phase 3c/organization
(déclaration hors `<package>` par défaut, relation bidirectionnelle
`mappedBy`). Le fichier `META-INF/orm.xml` reste un fichier unique partagé
entre tous les modules déjà traités — pas un fichier par module — ce qui
évite de re-tester l'auto-découverte à chaque incrément.

Point de vigilance pour `identity` et surtout `inventory` (mentionnés par
l'utilisateur comme prochains) : aucun des deux modules traités jusqu'ici
(`organization`, `catalog`) n'avait d'invariant métier sur ses entités.
`inventory.Stock`/`PurchaseOrder` (module suivant probable après
`identity`) en ont (`issue()`, `reserve()`, `requireReceivable()`, etc.,
déjà testés au niveau domaine pur) — la question ouverte reste entière et
n'a pas été éclairée par ces deux premiers incréments : ce sera la vraie
première fois que le découpage devra prouver qu'il préserve un
comportement plus riche qu'un simple accesseur de champ.

## 7. Zones d'ombre / décisions prises en cours de route

Aucune ambiguïté fonctionnelle. Le seul écart par rapport à l'estimation
fournie dans la consigne (227-45=182 attendu, 196 mesuré) est signalé au
§4 — écart de calcul du côté de l'estimation initiale, pas une anomalie du
résultat obtenu.

## 8. Prochaine étape

Module `catalog` terminé et vérifié vert sur son périmètre. **N'enchaîne
sur aucun autre module sans feu vert explicite** — le choix entre
`identity` et un autre module reste à trancher après ce rapport.
