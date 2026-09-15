# Notes de migration

Fichier de suivi des constats qui appartiennent a une phase future de la
migration Clean Architecture, releves en travaillant sur la phase courante
mais volontairement non traites tout de suite. Voir `CLAUDE.md` pour l'ordre
des phases.

## Trouve pendant Phase 2

Violations rendues visibles par `ArchitectureRulesTest` (voir
`docs/phase-2-report.md` pour le detail du run et la liste des regles) et
**non corrigees**, conformement a la contrainte de Phase 2. Toutes touchent
du code de production hors legacy plat (donc potentiellement en scope
Phase 3, sauf le point 2 qui est pour partie legacy).

### 1. EN COURS (Phase 3c, module par module) : les 21 entites de domaine des 8 modules sont directement annotees JPA

**4 des 21 entites resolues en Phase 3c/organization**
(`Organization`/`City`/`Site`/`Warehouse`, mapping deplace vers
`META-INF/orm.xml`, voir `docs/phase-3c-organization-report.md`) — **17
restent a traiter**, module par module, phases futures.

Constat d'origine (Phase 2) : **100% des entites de domaine des modules
"neufs"** (21 sur 21, une dans chaque agregat) portaient des annotations
`jakarta.persistence` directement sur la classe de domaine — `@Entity`,
`@Table`, et au niveau des champs `@Column`, `@ManyToOne`/`@OneToOne`/
`@OneToMany`/`@ManyToMany`, `@JoinColumn`, `@Enumerated`, `@Embedded`,
`@Version` (272 occurrences au total, repartition complete dans
`docs/phase-2-report.md` §2). C'etait directement contraire a CLAUDE.md
("domain/ n'importe jamais Spring ni jakarta.persistence") et vrai pour la
totalite des agregats, pas un cas isole :

`catalog.Article`, `catalog.Category`, `identity.Permission`,
`identity.Role`, `identity.UserRoleAssignment`, `inventory.Stock`,
`inventory.StockMovement`, `organization.City`, `organization.Organization`,
`organization.Site`, `organization.Warehouse`, `purchasing.PurchaseOrder`,
`purchasing.PurchaseOrderLine`, `purchasing.Supplier`, `sales.Customer`,
`sales.CustomerOrder`, `sales.CustomerOrderLine`, `sales.Sale`,
`sales.SaleLine`, `transfers.StockTransfer`, `transfers.StockTransferLine`.

**Ce que Phase 3c/organization a appris** (voir
`docs/phase-3c-organization-report.md` pour le detail complet) : le choix
entre (a) classes JPA distinctes + mapper et (b) mapping XML `orm.xml`
n'est PAS purement stylistique — il depend d'un fait structurel a verifier
module par module : `Organization` et `Site` sont referencees via
`@ManyToOne` en direct (pas seulement via DTO) depuis 5 AUTRES modules
(`catalog.Article/Category` -> `Organization` ; `sales.Sale/CustomerOrder`,
`inventory.Stock/StockMovement`, `purchasing.PurchaseOrder`,
`transfers.StockTransfer` -> `Site`). Hibernate exige que le type
litteralement reference par un `@ManyToOne` externe reste lui-meme mappe
comme entite — l'option (a) (nouvelle classe JPA a un autre FQCN) aurait
donc oblige a modifier ces 9 fichiers externes, chose interdite pour cet
increment. Seule (b) `orm.xml` permet de conserver EXACTEMENT le meme
FQCN/package pour la classe tout en retirant ses annotations, donc zero
impact sur les modules consommateurs. `City`/`Warehouse` n'avaient pas
cette contrainte mais ont recu le meme traitement, par coherence.
**A verifier a nouveau pour chaque module restant** : `catalog.Article` a
le meme profil que `Site`/`Organization` (referencee en `@ManyToOne` par 4
autres modules) — s'attendre a devoir refaire ce meme raisonnement, pas a
supposer que (a) convient par defaut.

Les invariants metier deja presents sur ces classes (`Stock.issue()`,
`PurchaseOrder.requireReceivable()`, etc., deja testes au niveau domaine
pur selon CLAUDE.md) devront survivre intacts a la separation.

### 2. ~~A corriger en Phase 3/5~~ RESOLU en Phase 3b : 16 injections par champ

`GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION` (regle
prete-a-l'emploi ArchUnit, qui detecte `@Autowired`/`@Value`/`@Inject`/
`@Resource` sur un champ — plus large que le seul `@Autowired` cite
litteralement dans CLAUDE.md, mais coherente avec l'esprit "injection par
constructeur uniquement") remonte 16 violations, **aucune dans les 8
modules "neufs"** — toutes dans le legacy/l'infrastructure transverse :

- `config.ApplicationRequestFilter` (2 champs `@Autowired`)
- `config.FlickrConfiguration` (4 champs `@Value`)
- `controller.AuthenticationController` (3 champs `@Autowired`)
- `services.auth.ApplicationUserDetailsService` (2 champs `@Autowired`)
- `services.impl.FlickrServiceImpl` (4 champs `@Value`)
- `utils.JwtUtil` (1 champ `@Value`)

**Corrige en Phase 3b** — les 6 classes converties en injection par
constructeur, mecanisme uniquement (voir `docs/phase-3b-report.md`).
`JwtUtil`/`ApplicationRequestFilter` n'ont recu aucun autre changement :
toujours aucun secret en dur, `@Value("${jwt.secret}")` toujours resolu
depuis la configuration externe, juste deplace du champ vers le parametre
de constructeur — le contenu sensible reste un sujet Phase 5, pas touche
ici.

### 3. ~~Deja connu (Phase 1), confirme par un mecanisme independant~~ — RESOLU en Phase 3a

`ArchitectureRulesTest.modules_must_be_free_of_cycles` (verification
ArchUnit "slices" pure, independante de Spring Modulith) detectait
exactement les **4 memes cycles** que `ModularityTests.verifiesModularStructure`
en Phase 1, avec la meme cause racine unique
(`catalog.application.impl.ArticleServiceImpl` accedant directement aux
repositories/entites internes de `purchasing`/`sales`, voir la section
"Trouve pendant Phase 1" ci-dessous). Les deux mecanismes de detection
(Spring Modulith et ArchUnit) etaient donc en accord total sur ce point.
**Corrige en Phase 3a** — voir `docs/phase-3a-report.md`. Les deux tests
passent au vert depuis ce commit.

## Trouve pendant Phase 1

Toutes les violations detaillees ci-dessous ont ete **rendues visibles** par
`ModularityTests.verifiesModularStructure()` (voir `docs/phase-1-report.md`
pour le detail complet du run brut) et **non corrigees**, conformement a la
contrainte de Phase 1 ("si des violations necessitent de deplacer du code
entre packages, ne le fais pas maintenant — c'est la Phase 3").

### ~~A corriger en Phase 3~~ RESOLU en Phase 3a : `catalog.application.impl.ArticleServiceImpl` accedait directement aux internes de `purchasing` et `sales`

Root cause unique de **les 4 seules violations reelles** (hors artefacts des
packages legacy plats) que `ApplicationModules.of(...).verify()` remonte
entre les 8 modules "neufs". La classe injecte directement :
- `purchasing.infrastructure.persistence.PurchaseOrderLineRepository`
  (repository JPA interne au module `purchasing`)
- `sales.infrastructure.persistence.SaleLineRepository` et
  `CustomerOrderLineRepository` (idem, module `sales`)

et manipule directement leurs entites de domaine
(`purchasing.domain.model.PurchaseOrderLine`,
`sales.domain.model.{SaleLine,CustomerOrderLine}`) dans ses methodes
`findHistoriqueVentes`, `findHistoriaueCommandeClient` (sic, faute de frappe
existante dans le nom de methode — a corriger en meme temps),
`findHistoriqueCommandeFournisseur` et `delete`
(`src/main/java/.../catalog/application/impl/ArticleServiceImpl.java`).

C'est une violation directe et non ambigue de la regle CLAUDE.md "Un module
n'accede jamais au repository ou a l'entite JPA d'un autre module". Elle
cree en prime un cycle de dependance catalog <-> purchasing et catalog <->
sales (purchasing/sales dependent legitimement de `catalog` pour
`ArticleDto`/`Article`, et `ArticleServiceImpl` depend en retour de leurs
internes).

**Resolution effective (Phase 3a)** : l'option (a) seule (exposer
`findLinesByArticleId` sur les facades publiques et faire consommer
`ArticleServiceImpl` via ces API plutot que les repositories) a ete
implementee en premier, mais **s'est averee insuffisante** — un test
empirique l'a confirme avant de committer quoi que ce soit de definitif :
`sales`/`purchasing` dependent deja legitimement de `catalog` (`ArticleDto`)
pour leurs propres lignes, donc tout appel de `catalog` vers `sales`/
`purchasing` — meme strictement limite a leur facade publique, sans toucher
a leurs internes — recree mecaniquement un cycle catalog {@literal <->}
sales/purchasing au niveau du graphe de dependance entre modules. Passer
par la facade publique corrige l'encapsulation (plus d'acces direct a un
repository/une entite d'un autre module) mais ne peut pas, a lui seul,
rendre le graphe acyclique tant que `catalog` conserve une raison
quelconque de dependre de `sales`/`purchasing`.

L'option (b) a donc ete appliquee en complement : les trois methodes
d'historique (`findHistoriqueVentes`/`findHistoriqueCommandeClient`/
`findHistoriqueCommandeFournisseur`) ont ete deplacees hors de `catalog`,
vers des controleurs legacy dedies vivant dans les modules qui possedent
reellement la donnee — `sales.presentation.rest.legacy.SalesArticleHistoryLegacyController`
(vente + commande client) et
`purchasing.presentation.rest.legacy.PurchaseOrderArticleHistoryLegacyController`
(commande fournisseur) — avec les memes URL HTTP qu'avant (aucun impact
contrat). Le garde-fou de suppression (`delete`) ne pouvait pas non plus
etre resolu par un simple appel aux facades (meme probleme de cycle) : il
repose desormais sur la contrainte `FOREIGN KEY (article_id) REFERENCES
article(id)` deja presente en base sur `sale_line`/`customer_order_line`/
`purchase_order_line` (V1__initial_schema.sql), la violation SQL etant
traduite en `InvalidOperationException(ARTICLE_ALREADY_IN_USE)`. Detail
complet, tests de non-regression ajoutes et resultat de
`ModularityTests`/`ArchitectureRulesTest` apres correction :
`docs/phase-3a-report.md`.

### @NamedInterface proposes mais non appliques (pas de consommateur actuel parmi les 8 modules)

Identifies en meme temps que les 12 deja appliques (voir
`docs/phase-1-report.md` §4), mais aucun autre module "neuf" n'en depend
aujourd'hui (seul le legacy, exclu du perimetre Phase 1, y accede) :
- `purchasing.application` / `purchasing.application.dto`
- `transfers.application` / `transfers.application.dto` /
  `transfers.domain.model`
- Tout package de `reporting` (module "feuille", personne n'en depend)

A appliquer des qu'un consommateur reel apparait (Phase 3, ou si le legacy
est un jour lui-meme modularise en Phase 4).

## Trouve pendant Phase 0

Tous les points ci-dessous ont ete **figes par un test de caracterisation**
(golden master) sous `src/test/java/.../legacy/*CharacterizationTest.java`
plutot que corriges, conformement a la regle "aucune ligne de production
modifiee" de Phase 0. Chaque test qui les caracterise documente le point
precis dans son propre commentaire.

### Bugs / comportements surprenants a traiter en Phase 5 (securite) ou en backlog

- **`AdresseValidator.validate`** (`validator/AdresseValidator.java:29-31`) :
  le controle cense verifier `codePostale` revalide en realite `adresse1`
  (copier-coller). Consequence : `codePostale` n'est **jamais** controle sur
  `Client`/`Fournisseur`, malgre un message d'erreur qui affirme le
  contraire. Figé par `ClientCharacterizationTest.createWithoutCodePostaleStillSucceedsBecauseOfAdresseValidatorBug`.
- **`MvtStkValidator.validate`** (`validator/MvtStkValidator.java:30`) :
  `dto.getTypeMvt().name()` est appele sans garde null. Un
  `POST /mvtstk/entree` sans champ `typeMvt` leve une `NullPointerException`
  non catchee, qui remonte en **500 brut** au lieu d'un message de
  validation propre (contrairement a tous les autres champs manquants du
  meme DTO). Figé par `MvtStkCharacterizationTest.entreeWithNullTypeMvtSurfacesAsRaw500InsteadOfCleanValidationError`.
- **Aucune verification d'unicite applicative sur les codes**
  `Sale.code`, `CustomerOrder.code`, `PurchaseOrder.code` (contrainte
  `UNIQUE` uniquement en base). Un code duplique remonte en **500 brut**
  (`DataIntegrityViolationException` -> handler generique, corps sans champ
  `code`) au lieu d'un `InvalidEntityException` propre en 400. Figé sur les
  trois flows (`VentesCharacterizationTest`, `CommandeClientCharacterizationTest`,
  `CommandeFournisseurCharacterizationTest`, methodes
  `...WithDuplicateCodeSurfacesAsRaw500WithoutErrorCode`).
- **Fuite de lecture cross-tenant** : aucun des endpoints `/all` /
  `/{id}` / `/filter/{code}` legacy (`Client`, `Fournisseur`, `Ventes`,
  `CommandeClient`, `CommandeFournisseur`) ne filtre par `idEntreprise` /
  tenant de l'appelant — n'importe quel utilisateur authentifie peut lire
  les donnees de n'importe quelle autre entreprise par id/code, et `/all`
  retourne l'integralite de la table tous tenants confondus, sans
  pagination. Figé sur les cinq flows (methodes
  `findAllReturnsRawArrayAcrossAllTenantsWithoutPagination`).
- **Aucune verification d'unicite sur `mail`** (`Client`/`Fournisseur`) :
  deux clients ou deux fournisseurs peuvent partager le meme mail sans
  erreur. Figé par `twoClientsWithSameMailBothSucceedBecauseNoUniquenessCheck`
  / `twoFournisseursWithSameMailBothSucceedBecauseNoUniquenessCheck`.
- **`idEntreprise` optionnel et silencieusement "perdu"** sur
  `Client`/`Fournisseur` : s'il ne resout vers aucune `Entreprise` connue,
  le `Customer`/`Supplier` sous-jacent est persiste avec `organizationId =
  null`, sans erreur, et reste visible via `/all`.

### Ecarts de contrat HTTP a examiner avant toute suppression du legacy (Phase 4)

- **`LigneCommandeFournisseurDto.commandeFournisseur`** est type sur
  l'entite JPA `CommandeFournisseur` (pas un DTO), sans `@JsonIgnore` —
  contrairement a `LigneCommandeClientDto.commandeClient` (DTO) et
  `LigneVenteDto.vente` (`@JsonIgnore`). En pratique le champ reste toujours
  `null` cote adapter (jamais renseigne par
  `CommandeFournisseurServiceImpl.findAllLignesCommandesFournisseurByCommandeFournisseurId`),
  donc pas d'exception Jackson observee, mais c'est fragile : tout code qui
  peuplerait un jour ce champ risque une serialisation d'entite JPA brute
  (fuite de champs internes, boucles de relations lazy). A corriger en
  meme temps qu'un futur nettoyage des DTO legacy.
- **`LigneCommandeFournisseurDto`** n'expose qu'un seul champ `quantite`
  (mappe depuis `quantiteCommandee` du module neuf) : `quantiteRecue` et
  `quantiteRestanteARecevoir` (presents cote `purchasing.PurchaseOrderLineDto`)
  sont silencieusement absents du contrat JSON legacy. Un consommateur HTTP
  historique de `/commandesfournisseurs/lignesCommande/{id}` n'a donc aucun
  moyen de connaitre l'etat de reception partielle d'une ligne.
- **`CommandeClientServiceImpl.findAllLignesCommandesClientByCommandeClientId`**
  reconstruit des `LigneCommandeClientDto` sans jamais renseigner
  `commandeClient` ni `idEntreprise` (les deux apparaissent `null` en JSON) —
  perte silencieuse par rapport a ce que l'ancien legacy retournait
  probablement (ces champs existent dans le DTO).
- **CommandeClient : `LIVREE` est inatteignable via le contrat legacy
  seul.** Aucun endpoint `/commandesclients/*` n'expose les transitions
  intermediaires `reserver`/`preparer`/`expedier` (elles n'existent que sous
  `/customer-orders/*`, module neuf). Un client historique du contrat
  legacy qui tenterait de driver une commande jusqu'a `LIVREE` sans jamais
  appeler le module neuf se heurtera systematiquement a
  `COMMANDE_CLIENT_TRANSITION_UNSUPPORTED`.
- **CommandeFournisseur : aucun endpoint legacy pour annuler une commande.**
  `PurchaseOrderService.cancel` existe et est expose sous
  `/purchase-orders/{id}/annuler` (module neuf), mais rien d'equivalent
  n'existe sous `/commandesfournisseurs/*`. `delete` renvoie toujours 400
  ("utilisez l'annulation"), qui n'a donc pas d'equivalent HTTP legacy.
- **Codes d'erreur "cote module neuf" qui fuitent a travers le contrat
  HTTP legacy**, jamais remappes vers un code `*_NOT_FOUND`/`*_NOT_VALID`
  proprement legacy : `GET /clients/{id}` -> `CUSTOMER_NOT_FOUND` (pas
  `CLIENT_NOT_FOUND`), `GET /fournisseurs/{id}` -> `SUPPLIER_NOT_FOUND`,
  `GET /commandesclients/{id}` -> `CUSTOMER_ORDER_NOT_FOUND`,
  `GET /commandesfournisseurs/{id}` -> `PURCHASE_ORDER_NOT_FOUND`,
  `POST /ventes/create` (0 ligne) -> `SALE_NOT_VALID` (pas
  `VENTE_NOT_VALID`). A garder en tete si un jour on decide d'exposer une
  documentation OpenAPI stricte du contrat legacy : les codes d'erreur
  documentes ne correspondent pas aux prefixes des endpoints.

### Constats RBAC/permissions (a valider en Phase 5)

- **Aucune verification de permission** sur les chemins de creation/
  validation `Sale.create`, `CustomerOrder.create`/`validate`,
  `PurchaseOrder.create`/`validate` (seules les actions qui touchent
  physiquement le stock — `SALE_CREATE` au moment de la vente,
  `CUSTOMER_ORDER_RESERVE/DELIVER/CANCEL`, `PURCHASE_ORDER_RECEIVE` — sont
  gated par `AuthorizationService`). Tout utilisateur authentifie peut donc
  creer des brouillons de vente/commande sur n'importe quel site, tant
  qu'il ne va pas jusqu'a l'etape qui bouge du stock physique.

### Ecarts entre CLAUDE.md et l'etat reel du code

- CLAUDE.md decrit la structure de module cible comme
  `domain/ -> application/ -> adapter/{in,out}/... -> config/`. La
  structure reellement en place dans les modules deja "neufs" (`inventory`,
  `purchasing`, `sales`, `transfers`, `organization`, `catalog`, `identity`)
  est `application/ -> domain/ -> infrastructure/persistence -> presentation/rest`
  (pas de package litteralement nomme `adapter`). A signaler avant la Phase
  3 (refactor module par module) : soit la convention documentee doit
  changer, soit un futur refactor doit renommer `infrastructure`/
  `presentation` en `adapter/out`/`adapter/in`.

## A traiter en Phase 3+ (hors scope Phase 0)

- `LigneCommandeClientValidator` (`validator/LigneCommandeClientValidator.java`) :
  classe morte, toujours vide (`// TODO to be implemented`), jamais
  appelee. A supprimer ou implementer lors du nettoyage du legacy plat
  (Phase 4).
- Codes d'erreur legacy jamais leves en pratique : `MVT_STK_NOT_FOUND`
  (aucun chemin "not found" dans `MvtStkServiceImpl`),
  `LIGNE_COMMANDE_CLIENT_NOT_FOUND`, `LIGNE_COMMANDE_FOURNISSEUR_NOT_FOUND`,
  `LIGNE_VENTE_NOT_FOUND`. A confirmer morts puis supprimer en Phase 4.
- Mappings d'enum a sens unique perdant de l'information : `TypeMvtStk`
  (legacy, 4 valeurs) ne peut pas representer tous les
  `StockMovementType` du module neuf (`RESERVATION`,
  `LIBERATION_RESERVATION`, `TRANSFERT_SORTIE`, `TRANSFERT_ENTREE`, etc.) —
  `MvtStkServiceImpl.toTypeMvtStk` retourne `null` pour ces types. De meme,
  `CustomerOrderStatus.RESERVEE/PREPAREE/EXPEDIEE` collapsent tous vers
  `EtatCommande.EN_PREPARATION` en lecture legacy.
