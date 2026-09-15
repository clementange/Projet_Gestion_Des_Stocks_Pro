# Phase 0 — Rapport de sortie de phase

Filet de securite avant migration Clean Architecture. Aucune ligne de
`src/main/java` n'a ete modifiee durant cette phase — uniquement des tests
et de la configuration de build/test (`pom.xml`, `src/test/java`).

## 1. Ce qui a ete livre

### 1.1 Testcontainers PostgreSQL (`./mvnw clean verify` sans Postgres local)

- `pom.xml` : ajout `spring-boot-testcontainers`, `org.testcontainers:postgresql`,
  `org.testcontainers:junit-jupiter` (scope `test`) ; version geree
  `testcontainers.version` relevee a `1.20.4` (la version geree par
  `spring-boot-starter-parent:3.3.5`, 1.19.8, negocie une API Docker 1.32
  rejetee par les daemons Docker recents — voir §3).
- `src/test/java/.../support/AbstractIntegrationTest.java` : conteneur
  PostgreSQL singleton (pattern JVM partagee), branche via
  `@DynamicPropertySource` (fonctionnalite du Spring TestContext Framework,
  compatible aussi bien avec les classes `@RunWith(SpringRunner.class)`
  JUnit4 vintage — utilisees partout dans ce depot — qu'avec du JUnit5 pur).
  Desactive `spring.flyway.baseline-on-migrate` sur le conteneur, pour que
  `V1__initial_schema.sql` et les migrations suivantes s'executent
  integralement sur une base neuve (ce reglage existant est pense pour la
  base de dev historique construite a la main avant Flyway, pas pour un
  conteneur jetable).
- Les **20 classes `@SpringBootTest` existantes** (toutes) ont ete
  retrofittees pour etendre `AbstractIntegrationTest` — aucune assertion
  modifiee, uniquement le socle d'infra. `ApiGestionDeStockApplicationTests`
  (placeholder inerte, `@SpringBootTest`/`@Test` commentes) n'a pas ete
  touchee.
- Verifie : `./mvnw clean verify` passe avec zero Postgres demarre a la
  main, uniquement Docker (le conteneur est cree/detruit par le test run).

### 1.2 JaCoCo

- `jacoco-maven-plugin:0.8.12` ajoute (`prepare-agent` lie a
  `test-compile`, `report` lie a `test`). Rapport genere sous
  `target/site/jacoco/` (`index.html`, `jacoco.csv`, `jacoco.xml`) a chaque
  `./mvnw clean verify`.

### 1.3 Tests de caracterisation ajoutes

**42 nouveaux tests**, tous verts, repartis en 6 nouvelles classes sous
`legacy/` (une par entite, pour ne pas faire grossir indefiniment
`LegacyControllersIntegrationTest.java` qui reste inchangee) :

| Classe | Tests | Endpoints couverts |
|---|---|---|
| `ClientCharacterizationTest` | 6 | `/clients/{create,all,delete}`, incoherence code 404, fuite cross-tenant, bug `AdresseValidator`, absence d'unicite mail |
| `FournisseurCharacterizationTest` | 5 | idem + DELETE (jusqu'ici non teste du tout sur ce controleur) |
| `VentesCharacterizationTest` | 6 | 404, DELETE toujours refuse, stock insuffisant via HTTP (avec verification de non-persistance), fuite `SALE_NOT_VALID`, code duplique -> 500, `/all` |
| `CommandeClientCharacterizationTest` | 8 | 404, 4 endpoints de mutation fine toujours 400, DELETE toujours refuse, `LIVREE` inatteignable, `/all`, `/lignesCommande` (perte de champs), client inconnu, code duplique |
| `CommandeFournisseurCharacterizationTest` | 9 | idem + le flow legal complet BROUILLON->VALIDEE->LIVREE (seul flow "commande" entierement pilotable via le legacy seul), rejet sur commande deja recue |
| `MvtStkCharacterizationTest` | 8 | corps de reponse complet, sortie/correction dans et hors limite de stock, signe ignore par les endpoints de correction, bug `typeMvt=null` (NPE -> 500), forme de `/filter/article/{id}` |

Chaque test documente en commentaire le comportement precis qu'il fige et,
le cas echeant, pourquoi (bug connu, changement de comportement deja assume
lors d'une phase de migration anterieure, incoherence de contrat).

### 1.4 Documentation

- `docs/migration-notes.md` (nouveau) : section "Trouve pendant Phase 0"
  avec tous les bugs/incoherences reperes en ecrivant les tests, classes par
  categorie (bugs a traiter en Phase 5, ecarts de contrat HTTP a examiner
  avant extinction du legacy en Phase 4, constats RBAC, ecart entre
  CLAUDE.md et la structure reelle des modules).

## 2. Etat final : `./mvnw clean verify`

```
Tests run: 116, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

(74 tests preexistants + 42 nouveaux, tous verts, executes contre le
conteneur PostgreSQL Testcontainers.)

## 3. Ecart technique rencontre et resolu : negociation API Docker

Le detecteur d'environnement Docker de Testcontainers (copie figee/shadee
de `docker-java-core` embarquee dans le jar `testcontainers`) envoie un
ping de compatibilite explicitement en API Docker `1.32`. Le Docker Engine
de cet environnement (29.8.0, API serveur 1.56) a retire le support des
API < 1.40, ce qui faisait echouer *tous* les tests `@SpringBootTest` avec
`IllegalStateException: Could not find a valid Docker environment`, avant
meme d'atteindre le code applicatif. Resolu en forcant la propriete systeme
`api.version=1.44` sur le fork Surefire (`argLine` dans `pom.xml`, en
composant avec `@{argLine}` pour ne pas ecraser l'injection de l'agent
JaCoCo). Documente en commentaire dans `pom.xml`. A surveiller si ce depot
tourne un jour sur un Docker Engine dont l'API maximale serait inferieure a
1.44 (peu probable, mais le cas echeant il faudrait baisser cette valeur).

## 4. Couverture JaCoCo mesuree — packages `domain`/`application`

Mesuree reellement via `target/site/jacoco/jacoco.csv` apres le run complet
(74+42 tests), agregee par module et sommee sur `domain` + `application` :

| Module | Instructions couvertes | % instructions | Lignes couvertes | % lignes |
|---|---|---|---|---|
| `inventory` | 982 / 2365 | 41.5% | 198 / 212 | **93.4%** |
| `purchasing` | 1142 / 3135 | 36.4% | 225 / 275 | 81.8% |
| `sales` | 1750 / 4703 | 37.2% | 346 / 434 | 79.7% |
| `transfers` | 819 / 2166 | 37.8% | 162 / 217 | 74.7% |
| `organization` | 1276 / 3736 | 34.2% | 255 / 366 | 69.7% |
| `identity` | 848 / 2172 | 39.0% | 165 / 249 | 66.3% |
| `catalog` | 721 / 2336 | 30.9% | 139 / 237 | 58.6% |

**Lecture** : la couverture en lignes (souvent 65-93%) est nettement plus
haute que la couverture en instructions (30-42%) pour tous les modules. Le
`application/dto` de chaque module est domine par des classes Lombok
`@Builder`/`@Data` volumineuses (equals/hashCode/toString/getters/setters
generes, methodes de builder jamais toutes exercees par les tests
existants) qui comptent lourd en nombre d'instructions bytecode sans
representer de logique metier reelle a tester — c'est une limite connue de
la metrique "instructions couvertes" sur du code genere. La couverture en
**lignes** est plus representative de l'effort de test reel ici.
`inventory` ressort nettement au-dessus des autres (93.4% lignes) car son
`domain/model.Stock` (invariants `issue`/`receive`/`reserve`/`correct`) est
deja intensivement teste au niveau domaine pur, conformement a CLAUDE.md.
`catalog` est le module le moins couvert (58.6% lignes) — candidat naturel
pour un renfort de tests en Phase 3 si son refactor est priorise tot.

Ces chiffres sont un **point de depart mesure**, pas un objectif : Phase 0
ne fixe aucun seuil minimal (ce sera, le cas echeant, une decision de phase
ulterieure).

## 5. Zones d'ombre / ambiguites rencontrees

Aucune ambiguite fonctionnelle bloquante n'a ete rencontree qui aurait
justifie d'interrompre la phase pour validation humaine. Deux categories de
constats ont ete deliberement **caracterisees telles quelles** plutot que
traitees comme des bugs, faute de pouvoir trancher seul si elles sont
volontaires :

- **La fuite de lecture cross-tenant** sur `/all`/`/{id}`/`/filter/{code}`
  pour les 5 entites metier (Client, Fournisseur, Ventes, CommandeClient,
  CommandeFournisseur) — aucune de ces routes ne filtre par tenant. Cela
  pourrait etre un vrai probleme de securite (voir Phase 5) ou un choix
  assume pour un legacy qui n'a jamais eu de notion stricte de
  cloisonnement inter-entreprise cote lecture. Figé, documente dans
  `migration-notes.md`, a trancher explicitement en Phase 5.
- **Le bug `AdresseValidator`** (codePostale jamais controle) et **le bug
  `MvtStkValidator`** (NPE sur `typeMvt` null) sont sans ambiguite des bugs
  (pas des regles metier), mais leur correction sort du perimetre "zero
  ligne de production modifiee" de Phase 0 — reportes en Phase 5 comme
  demande.

Un flux n'a pas pu etre caracterise en profondeur faute de temps/priorite
dans le budget de cette phase : le **scenario cross-module `TRANSFERT_ENTREE`**
(un mouvement de stock cree par le module `transfers` puis lu via
`/mvtstk/filter/article/{id}` devrait avoir `typeMvt: null`, le mapping
enum inverse legacy ne connaissant pas ce type) — identifie et documente
comme piste de test supplementaire dans le plan initial (P3, "optionnel"),
non implemente. De meme pour le test d'upload de photo
(`/save/{id}/{title}/clientStrategy`) avec `FlickrService` mocke via
`@MockBean` : confirme faisable techniquement (interface Spring propre,
pas d'appel reseau necessaire pour le test), mais non implemente car
marque P3 (optionnel) dans le plan approuve.

## 6. Prochaine etape

Phase 0 terminee et verifiee verte. **Ne pas enchainer sur la Phase 1
(Spring Modulith) sans feu vert explicite**, conformement a la regle de
travail.
