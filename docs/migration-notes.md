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

### 1. RESOLU en Phase 3c (7 increments, un module a la fois) : les 21 entites de domaine des 8 modules etaient directement annotees JPA

**21 des 21 entites resolues** : `Organization`/`City`/`Site`/`Warehouse`
(Phase 3c/organization, `docs/phase-3c-organization-report.md`),
`Article`/`Category` (Phase 3c/catalog, `docs/phase-3c-catalog-report.md`),
`Permission`/`Role`/`UserRoleAssignment` (Phase 3c/identity,
`docs/phase-3c-identity-report.md`), `Stock`/`StockMovement` (Phase
3c/inventory, `docs/phase-3c-inventory-report.md`),
`Supplier`/`PurchaseOrder`/`PurchaseOrderLine` (Phase 3c/purchasing,
`docs/phase-3c-purchasing-report.md`),
`Customer`/`CustomerOrder`/`CustomerOrderLine`/`Sale`/`SaleLine` (Phase
3c/sales, `docs/phase-3c-sales-report.md`), puis
`StockTransfer`/`StockTransferLine` (Phase 3c/transfers,
`docs/phase-3c-transfers-report.md`, dernier module) — mapping deplace
vers `META-INF/orm.xml` (fichier unique partage entre modules, pas un
fichier par module — Spring Boot n'auto-decouvre que ce nom exact).
`ArchitectureRulesTest` passe desormais integralement (6/6), la regle
`domain_must_not_depend_on_jakarta_persistence` n'a plus aucune violation.

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
**Confirme par Phase 3c/catalog** : `catalog.Article` avait effectivement
le meme profil que `Site`/`Organization` (referencee en `@ManyToOne` par 11
fichiers externes — 4 modules neufs + 4 entites legacy plates), et le meme
traitement `orm.xml` a fonctionne sans surprise, y compris pour la relation
bidirectionnelle `Category.articles` (`mappedBy`/`mapped-by`) et pour
declarer une entite hors du `<package>` par defaut du fichier XML partage
(class attribute pleinement qualifie). `Category`, elle, n'avait aucun
consommateur externe — meme technique appliquee par coherence, comme
`City`/`Warehouse`.

**Phase 3c/identity** : troisieme module traite, meme resultat sans
surprise. `Permission`/`Role`/`UserRoleAssignment` n'ont aucun
consommateur externe (verifie), mais mappees en `orm.xml` par coherence
comme `City`/`Warehouse`/`Category`. Nouveaute technique exercee ici pour
la premiere fois : `Role.permissions`, un `@ManyToMany`/`@JoinTable`
(table de jointure `role_permission`) — traduit en
`<many-to-many><join-table>` en XML, teste avec un jeu de permissions non
vide (pas juste un ensemble vide), fonctionne sans ajustement.

**A verifier a nouveau pour chaque module restant**, sans supposer que (a)
convient par defaut : le critere determinant reste "cette entite est-elle
referencee en `@ManyToOne`/`@OneToOne` direct par un autre module ?", pas
une propriete du module lui-meme.

**Phase 3c/inventory** : quatrieme module traite, premier avec de vrais
invariants metier (`Stock.issue/receive/reserve/releaseReservation/
correct`) et premier `@Version` de tout le schema (confirme unique via le
commentaire SQL de `V2__drop_stray_optimistic_lock_columns.sql`). Ni
`Stock` ni `StockMovement` n'ont de consommateur externe (contrairement a
`Site`/`Article`) : `orm.xml` n'etait donc pas une necessite technique ici,
mais applique par choix explicite de coherence. Nouveaute methodologique :
une etape 0 obligatoire a precede tout changement de mapping — un test
JUnit pur (`inventory/domain/model/StockTest`, 22 methodes, zero contexte
Spring) ecrit et verifie vert AVANT le retrait des annotations, puis
reverifie identique (0 assertion modifiee) apres — golden master direct
des invariants, independant des tests d'integration existants. A repeter
pour tout module restant portant une methode de domaine non triviale
(`purchasing.PurchaseOrder`, `sales.CustomerOrder`,
`transfers.StockTransfer`, etc.). Deux constructions XML inedites
validees : `<version>` (verrouillage optimiste) et `<unique-constraint>`
composite multi-colonnes au niveau `<table>` (`article_id`+`site_id`). La
garantie cross-module "jamais de survente" (`CrossModuleConcurrencyIntegrationTest`)
a ete reverifiee individuellement (3 executions separees, 3/3 vertes) sous
le nouveau mapping `<version>` XML — voir `docs/phase-3c-inventory-report.md`
§3 pour le detail du raisonnement.

**Phase 3c/purchasing** : cinquieme module traite, deuxieme avec de vrais
invariants metier (`PurchaseOrder.validate/cancel/requireReceivable/
markFullyReceived`, `PurchaseOrderLine.receive/getQuantiteRestanteARecevoir/
isFullyReceived`) — etape 0 golden-master repetee avec succes
(`PurchaseOrderTest` 13 tests, `PurchaseOrderLineTest` 9 tests, verifies
identiques avant/apres, 0 iteration de correction). Aucun consommateur
externe direct sur les trois entites (le seul import externe,
`services.impl.CommandeFournisseurServiceImpl`, ne reference que l'enum
`PurchaseOrderStatus` via la couche application/DTO). Ecart inedit trouve
et verifie sans consequence : `Supplier.numTel` a un nom de colonne
litteral (`@Column(name = "numTel")`, camelCase) different du nom
physique reel en base (`num_tel`, avec underscore) — resolu par
`SpringPhysicalNamingStrategy` (transforme tout identifiant logique
camelCase en snake_case, y compris les noms explicites), deja actif avant
ce changement. Regle retenue : l'XML doit reprendre le meme nom litteral
que l'annotation qu'il remplace, jamais le nom physique post-transformation
— `ddl-auto=validate` est le filet qui detecterait une erreur ici.

**Phase 3c/sales** : sixieme module traite, le plus gros en nombre
d'entites (5) mais le plus simple techniquement — une seule sur les cinq
porte des invariants non triviaux. Ecart trouve vs l'hypothese de depart
("CustomerOrder et Sale, a priori") : **`Sale` n'a aucune methode de
domaine** (simple `@Data`), seule `CustomerOrder` a une vraie machine a
etats (`validate/requireReservable/markReserved/requirePreparable/
markPrepared/requireShippable/markShipped/requireDeliverable/
markDelivered/cancel`) — etape 0 golden-master
(`sales/domain/model/CustomerOrderTest`, 21 tests) appliquee uniquement
a cette entite, verifiee identique avant/apres, 0 iteration de
correction. Aucun consommateur externe direct sur les 5 entites (meme
profil que purchasing : seul `CustomerOrderStatus` est importe
ailleurs, via la couche application/DTO). Deuxieme occurrence du cas
numTel/num_tel, cette fois sur `Customer` (ajoutee dans la meme migration
V5 que Supplier) : meme resolution, rien de nouveau. Aucun `@Version` ni
contrainte composite dans ce module.

**Phase 3c/transfers** : septieme et dernier module traite. `StockTransfer`
porte une machine a etats complete (`submit/approve/requirePreparable/
markInPreparation/requireShippable/markShipped/requireReceivable/
markReceived/cancel`, avec la particularite que `approve(userId)` mute
aussi `approvedByUserId` en plus du statut) ; `StockTransferLine` confirme
`@Data` pur par lecture directe (pas suppose par analogie). Etape 0
golden-master (`transfers/domain/model/StockTransferTest`, 20 tests),
verifiee identique avant/apres, 0 iteration de correction. Aucun
consommateur externe (recherche exhaustive, 0 resultat, pas meme un
import d'enum contrairement a purchasing/sales). Toutes les colonnes
physiques deja alignees sur `SpringPhysicalNamingStrategy` : pas de
troisieme cas numTel/num_tel. Aucun `@Version` ni contrainte composite.
**Bilan Phase 3c : 7 increments, 21 entites, 0 regression, 214 tests
verts, `ArchitectureRulesTest` integralement vert (6/6).**

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

## Phase 4a (migration model.Utilisateur -> identity.User) : trouve pendant l'increment

Voir `docs/phase-4a-report.md` pour le detail complet. Resume des points a
traiter, PAS corriges dans 4a (migration = reproduire le comportement
actuel a l'identique) :

### Deux decouvertes de securite hors perimetre de 4a, zero-tolerance CLAUDE.md

- **`EntrepriseServiceImpl.generateRandomPassword()` retourne une
  constante en dur** (`"som3R@nd0mP@$$word"`) au lieu d'utiliser
  `SecureRandom` — exactement le pattern interdit par CLAUDE.md
  ("Securite — zero tolerance"). Tout utilisateur admin bootstrap a la
  creation d'une Entreprise recoit ce mot de passe, identique pour tous
  les tenants, en clair dans le code source. **12 fichiers de test**
  dependent de cette valeur litterale (`adminToken()` un peu partout).
  Correctif reporte : suppose de redessiner le flux de bootstrap admin
  (comment communiquer un mot de passe reellement aleatoire ?) et de
  reecrire les 12 fichiers de test — hors perimetre d'un increment de
  migration, candidat pour un increment dedie.
- **`SecurityConfiguration.corsFilter()`** combine `allowCredentials(true)`
  avec `allowedOriginPatterns("*")` — combinaison interdite par
  CLAUDE.md, deja commentee dans le code (`// Don't do this in
  production`). Non touche (hors fichier-scope de 4a).

### Bugs herites sur le flux d'authentification, reproduits tels quels

- `changerMotDePasse` (`/utilisateurs/update/password` et
  `/users/{id}/password`) : aucune verification que l'appelant est la
  cible ou un administrateur (IDOR) — reproduit et teste tel quel dans
  `UtilisateurAuthenticationCharacterizationTest`.
- `create`/`delete`/`find*` sur `/utilisateurs/*` **et** `/users/*` :
  aucune verification de permission au-dela de `authenticated()` — meme
  situation sur la surface neuve, par choix explicite de ne pas durcir un
  endpoint neuf au-dela de ce que l'existant offrait deja pendant une
  migration.
- **Asymetrie decouverte pendant cet increment** : `POST
  /auth/authenticate` avec un mauvais mot de passe -> 400
  `BAD_CREDENTIALS` (gere explicitement par `RestExceptionHandler`), mais
  avec un email inconnu -> **500**, `code: null` (l'`EntityNotFoundException`
  levee par `UserService.findByEmail` a l'interieur de
  `ApplicationUserDetailsService.loadUserByUsername` n'est pas une
  `UsernameNotFoundException`, donc `DaoAuthenticationProvider` ne
  l'attrape pas — elle remonte au handler generique). Comportement
  pre-existant (deja present avant la migration), reproduit a l'identique
  apres, non corrige.

### RolesDto/model.Roles orphelins

`Utilisateur.roles` (legacy, `@OneToMany` vers `model.Roles`) n'a jamais
pu etre persiste : aucun `RolesRepository` n'existe dans le code. Retire
de `identity.User` et de `dto.UtilisateurDto` (silencieusement, aucun
test n'y touchait). `model.Roles`/`dto.RolesDto` deviennent totalement
orphelins, candidats a suppression en finition ulterieure (pas fait ici).

### model.Utilisateur/UtilisateurRepository orphelins

Meme etat que `model.CommandeFournisseur`/`CommandeFournisseurRepository`
depuis la Phase 21 : toujours annotes JPA sur la table `utilisateur`,
mais plus aucun code ne les reference que leur propre declaration.
Laisses en place (pas de suppression dans cet increment).

## Phase 4b (migration model.Entreprise -> tenant, mot de passe admin en dur) : trouve pendant l'increment

Voir `docs/phase-4b-report.md` pour le detail complet.

### Mot de passe admin en dur CORRIGE (pas seulement signale)

`EntrepriseServiceImpl.generateRandomPassword()` (constante en dur,
signalee comme decouverte de securite hors perimetre en Phase 4a) est
supprimee : le mot de passe admin vient desormais de l'appelant
(`TenantRegistrationRequest.motDePasse`/`confirmMotDePasse`), encode via
le `PasswordEncoder` deja branche dans `identity.UserServiceImpl.save()`.
Politique nouvelle : 8 caracteres minimum + correspondance des deux
champs, pas d'exigence de complexite.

### model.Entreprise/EntrepriseRepository orphelins (meme traitement que model.Utilisateur en Phase 4a)

**6 adaptateurs legacy** (`ClientServiceImpl`, `FournisseurServiceImpl`,
`VentesServiceImpl`, `MvtStkServiceImpl`, `CommandeFournisseurServiceImpl`,
`CommandeClientServiceImpl`) dependent directement d'`EntrepriseRepository`
pour traduire `idEntreprise <-> organizationId` (methodes
`resolveOrganizationId`/`resolveIdEntreprise` dupliquees a l'identique
dans les 6 fichiers). Decision : `model.Entreprise`/`EntrepriseRepository`
restent intacts, toujours `@Entity`, non touches par cet increment - les
6 fichiers n'ont eu aucune modification a subir. Migration future
possible mais non necessaire.

### EntrepriseService/Controller/Api/Dto/Validator supprimes (pas gardes en adaptateur)

Contrairement au pattern etabli en Phase 3c/4a (garder un adaptateur fin
sur l'ancien contrat), la couche `EntrepriseService`/`EntrepriseController`/
`EntrepriseApi`/`EntrepriseDto`/`EntrepriseValidator` a ete entierement
supprimee : `/entreprises/create` sans l'orchestration RBAC/Organization
n'aurait plus eu aucune utilite fonctionnelle. Ses 2 vrais consommateurs
(`SaveEntreprisePhoto`, `UtilisateurServiceImpl`/`UtilisateurDto.entreprise`)
sont rebranches directement sur `tenant.application.TenantService`/
`TenantDto`.

### Bug latent trouve et elimine structurellement (pas corrige comme un correctif cible)

`EntrepriseServiceImpl.save()` ne distinguait jamais creation/mise a jour
: un appel avec un `id` deja existant (le cas de
`SaveEntreprisePhoto.savePhoto()`, upload photo) redeclenchait toute
l'orchestration (nouvelle Organization miroir, nouveau Site, nouvel
admin, nouvelle attribution RBAC) a chaque changement de photo. Elimine
par la separation `TenantService.save()` (upsert plat, sans
orchestration) / `TenantRegistrationService.register()` (orchestration
complete) - consequence du refactor demande, pas un correctif ajoute.

### Gap Spring Modulith pre-existant, corrige

`identity.application.dto` (contenant `UserDto`/`UserRoleAssignmentDto`/
`RoleDto`) n'avait jamais eu de `package-info.java`/`@NamedInterface`,
contrairement a `organization.application.dto`. Le gap existait avant cet
increment mais n'avait jamais ete exerce (seul du code legacy plat, hors
perimetre ArchUnit/Modulith, appelait ces types) ; `tenant` est le premier
module reellement enregistre a le faire, ce qui a revele le gap.
Corrige : `identity/application/dto/package-info.java` ajoute,
`@NamedInterface("dto")`, meme modele qu'`organization.application.dto`.

### 14 fichiers de test adaptes (contrat /entreprises/create -> /tenants/register)

Un de plus que les "12" recenses au depart :
`UtilisateurAuthenticationCharacterizationTest` (cree pendant la Phase 4a)
en dependait aussi. Chaque `adminToken()`/`adminTenant()`/`tokenFor()`
poste desormais vers `/tenants/register` avec un mot de passe fourni par
le test lui-meme. Consequence directe et necessaire d'un contrat
volontairement change, documentee comme telle - voir
docs/phase-4b-report.md §5.

## Phase 4c (rehoming upload de photo, module media/MinIO) : trouve pendant l'increment

Voir `docs/phase-4c-report.md` pour le detail complet.

### Cles Flickr en clair dans application.yml, RESOLU (troisieme trouvaille de securite de la Phase 4)

`application.yml` contenait les 4 cles Flickr (`apiKey`, `apiSecret`,
`appKey`, `appSecret`) en dur, sans `${VAR_ENV:...}`, absentes de
`.env.example` - violation directe du zero-tolerance CLAUDE.md sur les
secrets. Resolu de fait par la suppression complete de l'integration
Flickr (le module `media`/MinIO la remplace entierement), pas par un
correctif isole. Ces cles restent visibles dans l'historique git ; une
purge d'historique n'a pas ete faite (hors perimetre, non demandee).

### config/FlickrConfiguration.java : code mort supprime

`@Configuration` etait commente sur la classe elle-meme (ligne 22) :
jamais un bean Spring enregistre, meme avant cet increment. Trouve en
compilant apres suppression de la dependance Maven `flickr4java`, pas
lors de l'investigation initiale. Supprime avec le reste de l'integration
Flickr.

### Bug latent trouve et elimine structurellement : upload photo utilisateur corrompait le mot de passe

`identity.UserServiceImpl.save()` (herite tel quel de l'ancien
`UtilisateurServiceImpl`, jamais corrige) re-encode le mot de passe a
chaque appel et rejette systematiquement en doublon d'email (la
verification ne s'exclut jamais elle-meme). L'ancien
`SaveUtilisateurPhoto` appelait ce `save()` pour persister une simple URL
de photo : chaque upload de photo utilisateur corrompait donc
silencieusement le mot de passe. Elimine par `UserService.updatePhoto()`,
une methode dediee qui mute uniquement le champ `photo` sans repasser par
`save()` - meme motif que `changePassword()` deja existant. Regression
testee explicitement (re-authentification avec le mot de passe original
apres upload photo) - voir docs/phase-4c-report.md §5.

### Registre Docker Hub minio/minio inaccessible sans authentification

MinIO Inc. a restreint l'acces anonyme a ses images Docker Hub (connexion
desormais requise) suite a un changement de licence en 2024 - confirme
par un `docker pull minio/minio:latest` manuel en echec. `quay.io/minio/minio`
est le miroir public gratuit officiel, utilise a la place dans
`AbstractIntegrationTest`, avec `.asCompatibleSubstituteFor("minio/minio")`
pour satisfaire la verification de compatibilite du module Testcontainers
MinIO. A retenir pour tout futur travail Docker dans cet environnement.

### ClientPhotoLegacyController/FournisseurPhotoLegacyController : ecart de forme assume

Les deux nouveaux controleurs legacy (`sales.presentation.rest.legacy`,
`purchasing.presentation.rest.legacy`) retournent respectivement
`CustomerDto`/`SupplierDto` (module neuf), pas `ClientDto`/`FournisseurDto`
(legacy), contrairement aux autres endpoints `/clients/*`/`/fournisseurs/*`.
Assume : ces routes (`POST /{id}/photo`) sont entierement nouvelles,
aucun contrat existant a preserver byte-for-byte (l'ancien mecanisme
utilisait une URL differente, `/save/{id}/{title}/{context}`, supprimee).

## Phase 4d (suppression des 6 adaptateurs legacy devenus superflus) : trouve pendant l'increment

Voir `docs/phase-4d-report.md` pour le detail complet.

### La premisse "code mort" etait vraie pour une couche, fausse pour une autre

`catalog.ArticleServiceImpl.delete()` (code module-neuf) et
`SalesArticleHistoryLegacyController`/`PurchaseOrderArticleHistoryLegacyController`
(Phase 3a) dependent directement de `LigneVenteRepository`/
`LigneCommandeClientRepository`/`LigneCommandeFournisseurRepository` - ces 3
entites "ligne" et leurs repositories/DTO restent en place, pas supprimables.
Chaine de dependance de type Java (@ManyToOne, champs DTO) decouverte en
lisant le code, pas supposee : `LigneVente.vente : Ventes`,
`LigneCommandeClient.commandeClient : CommandeClient`,
`CommandeClient.client : Client`, `LigneVenteDto.vente : VentesDto` (reellement
serialise), `LigneCommandeClientDto.commandeClient : CommandeClientDto`
(@JsonIgnore mais le type doit exister), `CommandeClientDto.client : ClientDto`
(reellement serialise). Consequence : `model.Client`/`Fournisseur`/`Ventes`/
`CommandeClient`/`CommandeFournisseur` ET `dto.ClientDto`/`VentesDto`/
`CommandeClientDto` restent tous en place, orphelins au meme titre que
`model.Utilisateur`/`model.Entreprise` deja documentes plus haut - seule la
couche adaptateur HTTP (Controller/Api/Service/ServiceImpl/Validator) a ete
supprimee pour ces 5 chaines. Seule `MvtStk` (rien ne pointe vers elle comme
type) a ete supprimee entierement, model.MvtStk inclus.

### Asymetrie Client/Fournisseur trouvee, exploitee sans etre corrigee

`LigneCommandeFournisseurDto.commandeFournisseur` est type sur l'entite JPA
brute `model.CommandeFournisseur` (deja signale comme fragile), alors que son
equivalent cote Client (`LigneCommandeClientDto.commandeClient`) est type sur
le DTO `CommandeClientDto`. Consequence concrete : `dto.FournisseurDto` et
`dto.CommandeFournisseurDto` sont reellement supprimables (verifie par grep
exhaustif), `dto.ClientDto` et `dto.CommandeClientDto` ne le sont pas. Perimetre
final asymetrique entre les deux chaines, documente tel quel plutot que lisse.

### sales.Customer/purchasing.Supplier n'avaient aucun controleur module-neuf

`ClientController`/`FournisseurController` (legacy) etaient leur unique
surface HTTP - `CustomerController`/`SupplierController` (`/customers/*`,
`/suppliers/*`) crees dans cet increment, purs, sans logique portee (
`ClientServiceImpl`/`FournisseurServiceImpl` ne faisaient deja que de la
traduction idEntreprise/organizationId).

### 7 fichiers de test caracterisant un contrat supprime : supprimes, pas reecrits

`ClientCharacterizationTest`, `FournisseurCharacterizationTest`,
`VentesCharacterizationTest`, `CommandeClientCharacterizationTest`,
`CommandeFournisseurCharacterizationTest`, `MvtStkCharacterizationTest`,
`LegacyControllersIntegrationTest` caracterisaient un contrat HTTP qui n'existe
plus - leur couverture applicative est deja assuree au niveau service par des
tests preexistants non touches par cet increment
(`CustomerServiceImplTest`/`SupplierServiceImplTest`/`SaleServiceIntegrationTest`/
`CustomerOrderServiceIntegrationTest`/`PurchaseOrderServiceIntegrationTest`/
`InventoryFacadeIntegrationTest`). `ArticleHistoryLegacyEndpointsTest` et
`PhotoAttachmentCharacterizationTest` (Phase 4c) ont eu leurs helpers de seed
HTTP adaptes aux nouvelles routes, le sujet teste par ces deux fichiers n'a pas
change.

## Phase 5a (backlog securite, correctifs isoles) : trouve pendant l'increment

Voir `docs/phase-5a-report.md` pour le detail complet. Premier increment du
backlog securite accumule depuis Phase 0 (voir sections "Trouve pendant Phase
0" et "Phase 4a" plus haut) - 5 correctifs a faible risque, CORS/AdresseValidator/
IDOR mot de passe/asymetrie auth/unicite code+mail, tous CORRIGES (pas juste
signales).

### Le backlog securite est plus large que documente a l'origine : leak cross-tenant systemique

Avant d'implementer 5a, verification independante de `interceptor/Interceptor.java`
(zone gelee CLAUDE.md) : son allowlist ne couvre que 10 prefixes de table
legacy (`article`, `category`, `mvtstk`, `commandeclient`,
`commandefournisseur`, `lignecommandeclient`, `lignecommandefournisseur`,
`lignevente`, `ventes`, `utilisateur`). **Aucune table module-neuf n'y figure**
(`customer`, `supplier`, `sale`, `customer_order`, `purchase_order`, `tenant`,
`organization`, `site`, `warehouse`, `stock`, `user`) - confirme en lisant
`findAll()` dans `CustomerServiceImpl`, `SupplierServiceImpl`, `SaleServiceImpl`,
`CustomerOrderServiceImpl`, `PurchaseOrderServiceImpl`, `TenantServiceImpl`,
`OrganizationServiceImpl`, `SiteServiceImpl`, `WarehouseServiceImpl`,
`InventoryFacadeImpl`, `UserServiceImpl` : aucun filtrage `organizationId` nulle
part, ni via l'interceptor ni en code Java. Le constat d'origine ("5 flows
legacy") sous-estimait tres largement le perimetre reel : c'est essentiellement
tout endpoint de liste/lecture module-neuf qui est concerne, y compris
`/tenants/all` (fuite du registre des tenants lui-meme) et les deux
controleurs crees en Phase 4d (`/customers/all`, `/suppliers/all`). Traite en
Phase 5b, increment dedie (voir docs/phase-5a-report.md §0 et §8) - pas dans
5a, deliberement, vu la taille et le fait que toute solution touche soit
`Interceptor.java` (gele), soit la signature de `findAll()` dans une dizaine
de services a la fois.

### IDOR mot de passe corrige en self-only, pas d'override admin

`/utilisateurs/update/password` et `/users/{id}/password` ne verifiaient
l'identite de l'appelant nulle part. Corrige en self-only (l'appelant doit
etre la cible) plutot qu'en introduisant une nouvelle permission RBAC pour un
override admin - choix explicite pour ne pas ouvrir une nouvelle surface RBAC
dans cet increment (deja exclu du perimetre de 5a). "Admin reinitialise le mot
de passe d'un autre utilisateur" reste un gap non traite, a rattacher a l'item
RBAC deja identifie plus haut ("Constats RBAC/permissions, a valider en Phase
5"), pas une regression introduite ici.

### Test verrouillant l'IDOR renomme et inverse (cas ou la regle "ne jamais modifier un test" autorise la modification)

`UtilisateurAuthenticationCharacterizationTest.changerMotDePasseHasNoOwnershipCheckIdorReproducedAsIs`
(Phase 4a) verrouillait explicitement le comportement bugue. Renomme en
`changerMotDePasseRejectsWhenCallerIsNotTargetUser`, assertion inversee (400
au lieu de 200, mot de passe de la victime inchange). Meme traitement pour
`loginFailsForUnknownEmailWith500NotBadCredentials` -> `...With400BadCredentialsSameAsWrongPassword`.
Les deux sont le cas exact ou la regle "ne jamais modifier un test existant
sans comprendre pourquoi il echouait" autorise la modification : le
comportement verrouille a ete volontairement change dans ce meme increment,
pas contourne a l'aveugle.

### Asymetrie 500/400 sur email inconnu : aussi un oracle d'enumeration de comptes

Au-dela de l'incoherence de statut HTTP deja documentee en Phase 4a, cette
asymetrie permettait de deviner si un email existait dans le systeme sans
jamais se connecter (500 = email connu, 400 = email inconnu ou mot de passe
faux, indistinctement). Corrige comme effet de bord du meme correctif
(`ApplicationUserDetailsService` traduit desormais l'exception en
`UsernameNotFoundException`, que Spring Security masque deja par defaut en
`BadCredentialsException`).

## Phase 5b-1 (frontiere tenant dans la couche RBAC) : trouve pendant l'increment

Voir `docs/phase-5b1-report.md` pour le detail complet. Root cause du leak
cross-tenant annonce en Phase 5a (§"Le backlog securite est plus large que
documente a l'origine") : traite separement de 5b-2 (findAll/findById sans
verification RBAC du tout), qui reste ouvert.

### Le bypass etait plus severe qu'un leak de lecture : contournement RBAC complet, en ecriture aussi

`AuthorizationServiceImpl.hasPermission` traitait `ScopeType.GLOBAL` comme
global a TOUTE l'application, pas a la seule organisation de l'affectation.
Tout admin de tenant (bootstrap avec un role GLOBAL depuis Phase 4b)
pouvait donc APPELER `POST /purchase-orders/{id}/valider`,
`/customer-orders/{id}/livrer`, `/stock-transfers/{id}/ship`, `/roles/*`
etc. sur les ressources de n'importe quel AUTRE tenant - pas seulement lire
leurs donnees. `UserRoleAssignment`/`Role`/`Permission` ne portaient aucune
notion d'organisation ; le JWT porte `idEntreprise` (Tenant.id) mais
`ApplicationRequestFilter` ne l'exploitait que pour le MDC consomme par
`Interceptor.java` (les 10 tables legacy), jamais pour l'autorisation RBAC.

### organizationId ajoute a UserRoleAssignment, filtre AVANT toute logique GLOBAL/scope

`AuthorizationServiceImpl.hasPermission`/`hasGlobalAccess` filtrent
desormais les affectations du caller par `organizationId` avant d'appliquer
la logique GLOBAL preexistante - GLOBAL reste "partout", mais seulement
parmi les affectations de l'organisation de l'appelant. `organizationId`
resolu une fois au login (`ApplicationUserDetailsService`, via le detour
`Tenant.id -> Tenant.organizationId` deja utilise par le bootstrap RBAC en
Phase 4b) et porte sur `ExtendedUser`/le JWT. 9 points d'appel mis a jour
(3 controleurs RBAC, Sale/CustomerOrder/PurchaseOrder/StockTransfer/
Reporting), `TenantRegistrationServiceImpl` fixe organizationId sur
l'affectation ADMINISTRATEUR/GLOBAL bootstrap.

### 8 fichiers de test adaptes pour fournir un organizationId reel

Nouvelle contrainte FK (`fk_user_role_assignment_organization`) : plusieurs
tests utilisaient des identifiants d'organisation entierement fabriques
(litteraux type `9001L`) jamais lies a une vraie ligne `organization` -
`AuthorizationServiceIntegrationTest` a du etre corrige pour creer de
vraies organisations via `OrganizationService` avant d'y attacher des
affectations RBAC. Les 7 autres fichiers de test utilisaient deja des
organisations reelles (creees via `organizationService.save(...)`), donc
seul le threading du parametre a ete necessaire.

## Phase 5b-2a (lecture scopee par organisation, entites a reference directe) : trouve pendant l'increment

Voir `docs/phase-5b2a-report.md` pour le detail complet. Premier des trois
increments de 5b-2 (Article/Category/Customer/Supplier - reference directe
a Organization) - 5b-2b (derivation via Site) et 5b-2c (cas speciaux
Organization/Tenant/User/Role/Permission) restent ouverts.

### La creation ne fixait jamais l'organisation cote serveur - trouve en implementant le filtrage en lecture

`ArticleController.save()`/`CategoryController.save()`/
`CustomerController.create()`/`SupplierController.create()` faisaient
confiance au client pour fournir l'organisation dans le corps de la
requete (ou ne la fournissaient jamais). Une fois la lecture filtree par
organisation, toute entite creee sans organisation explicite devenait
invisible a son propre createur - confirme concretement par l'echec de 4
suites de tests d'integration existantes (`ArticleHistoryLegacyEndpointsTest`,
`BusinessModulesHttpIntegrationTest`, `CustomerSupplierControllerCharacterizationTest`,
`PhotoAttachmentCharacterizationTest`) qui creent puis relisent
immediatement sans jamais fournir d'organisation dans le JSON. Corrige en
forcant l'organisation depuis `principal.getOrganizationId()` cote
controleur, TOUJOURS en ecrasant ce que le client aurait fourni (pas
seulement en cas d'absence) - sinon un appelant pourrait injecter des
donnees dans une autre organisation en fournissant un id arbitraire.
Correctif juge necessaire et borne (consequence directe du filtrage en
lecture), pas une extension vers la securisation generale des ecritures -
l'absence de verification de permission sur create/update/delete de ces 4
entites reste le gap RBAC deja catalogue en Phase 5b-1 §8, non traite ici.

### findById(Long) sans organisation conserve pour updatePhoto

`ArticleServiceImpl`/`CustomerServiceImpl`/`SupplierServiceImpl.updatePhoto`
appelaient deja `findById(id)` en interne (Phase 4c) - cette variante est
restee inchangee plutot que de forcer un filtrage sur un chemin d'ecriture
hors perimetre de cet increment. `CategoryServiceImpl` n'avait aucun appel
interne de ce type, ses trois methodes de lecture ont ete changees
directement.

## Phase 5b-2b (lecture scopee par organisation, entites a derivation via Site) : trouve pendant l'increment

Voir `docs/phase-5b2b-report.md` pour le detail complet. Deuxieme des trois
increments de 5b-2 (Sale/CustomerOrder/PurchaseOrder/StockTransfer/Site -
derivation `Site -> City -> Organization`, + les deux lectures de Stock
exposees par InventoryController) - 5b-2c (cas speciaux Organization/Tenant/
User/Role/Permission) reste ouvert.

### Contournement GLOBAL trouve sur Sale.create, ferme independamment de hasPermission

`SaleServiceImpl.create()` verifiait deja `hasPermission(..., ScopeType.SITE,
siteId, organizationId)`, mais `AuthorizationServiceImpl.hasPermission`
(Phase 5b-1) filtre d'abord les affectations du caller sur sa PROPRE
organisation puis applique GLOBAL sans jamais revalider que le `scopeId`
demande appartient a cette organisation - consequence mecanique du correctif
5b-1 sur un parametre que rien ne validait deja. Un appelant GLOBAL dans
l'organisation A pouvait donc creer une vente sur un site de l'organisation
B. Ferme par `requireSiteInOrganization`, verification independante placee
AVANT `hasPermission`, qui ne depend d'aucune logique RBAC.

### CustomerOrder/PurchaseOrder/StockTransfer.create n'avaient aucune verification de permission ni de site - confirme et etendu le gap deja catalogue

Contrairement a `Sale.create` (qui avait au moins `hasPermission`, defaillant
comme ci-dessus), les trois autres `create()` n'avaient litteralement aucune
verification, ni de permission ni d'appartenance de site - n'importe quel
appelant authentifie pouvait creer une commande client/fournisseur ou un
transfert sur le site de n'importe quelle autre organisation. La nouvelle
verification `requireSiteInOrganization` (memes signature et comportement
que pour Sale, ErrorCodes `*_ACCESS_DENIED` deja existants) ajoute une
frontiere tenant etroite ; le gap RBAC plus large ("qui peut creer", pas
seulement "sur quel site") reste celui deja catalogue en Phase 5b-1 §8 et
Phase 0, non traite ici. StockTransfer a deux sites (origine ET destination)
: les deux sont verifies independamment, aucun des deux ne peut appartenir a
une autre organisation.

### InventoryController : decouverte non cataloguee jusqu'ici, plus severe que les gaps connus (ecriture cross-tenant sans restriction)

Les 5 endpoints (`getStock`, `findMovements`, `receive`, `issue`, `correct`)
n'avaient AUCUNE verification de principal/permission - plus severe que les
autres gaps de cette phase car `receive`/`issue`/`correct` sont des
ECRITURES physiques sur le stock, pas de simples lectures. Perimetre de cet
increment : corrige uniquement les 2 lectures (`getStock`/`findMovements`,
via `siteService.findById(idSite, organizationId)` reutilise directement,
sans nouvelle methode dediee) dans le cadre de la couverture "Stock" prevue.
Les 3 ecritures restent NON corrigees, nouvellement cataloguees en backlog
securite (a rapprocher du gap RBAC create deja connu, mais distinct : ici
c'est un controleur entier sans AuthenticationPrincipal du tout, pas
seulement une permission manquante).

### SiteController.save() : gap d'appartenance non traite, delibere

Aucune verification que la `City` referencee appartient a l'organisation de
l'appelant - chaine `City -> Organization` plus profonde que les 6 entites
nommees de cet increment. Javadoc explicite ajoutee sur la classe plutot que
traite silencieusement. `OrganizationController` (save/findById/findAll/
delete) reste lui aussi sans aucune verification - racine du probleme de
fixture ci-dessous, cas special "self-only" deja prevu pour 5b-2c.

### Root cause additionnelle trouvee en verifiant : fixtures de test qui creaient une organisation deconnectee du principal

`./mvnw clean verify` a revele 3 echecs apres implementation (sur 210
tests), tous dans des fixtures, pas en production. `BusinessModulesHttpIntegrationTest`
et `OrganizationControllersHttpIntegrationTest` creaient une NOUVELLE
`Organization` via `POST /organizations/create` (toujours sans aucune
verification) pour construire leur hierarchie Site/City de test, distincte
de l'organisation propre du principal authentifie (resolue a l'inscription
du tenant, portee par son JWT). Fonctionnait par accident avant cet
increment (rien ne verifiait qu'un site appartenait a l'organisation de
l'appelant) ; rejete a raison une fois la verification en place. Corrige en
decodant `organizationId` directement du JWT deja detenu par le test et en
construisant la hierarchie sous cette organisation plutot que d'en creer une
nouvelle - reflete l'usage reel, pas un contournement de l'assertion
observee (regle CLAUDE.md "ne jamais adapter un test sans comprendre
pourquoi il echouait" respectee : la cause etait bien comprise avant de
toucher au test).

## Phase 5b-2c (cas speciaux : Organization/Tenant self-only, User via Tenant.id, Role/Permission/UserRoleAssignment) : trouve pendant l'increment

Voir `docs/phase-5b2c-report.md` pour le detail complet. Troisieme et dernier increment de 5b-2 -
5b-2 (lecture scopee par organisation + RBAC) est desormais complet dans son integralite (5a,
5b-1, 5b-2a, 5b-2b, 5b-2c).

### Quatre motifs distincts, pas un seul applique 6 fois

Contrairement a 5b-2a/2b (un seul motif "filtrer par organizationId derive"), 5b-2c a demande
quatre traitements differents selon la nature de chaque entite : self-only (Organization/Tenant,
compare directement l'id demande a celui de l'appelant), scoping via un scalaire different
(User.idEntreprise = Tenant.id, PAS organizationId, absent de l'entite), gate de permission sans
filtrage de ligne (Role/Permission : aucun champ organizationId, catalogue global partage entre
tous les tenants confirme par le bootstrap ADMINISTRATEUR), et gate de permission PLUS garde-fou
d'appartenance par ligne (UserRoleAssignment : porte reellement organizationId depuis 5b-1).

### Role/Permission : violation directe du zero-tolerance CLAUDE.md sur les lectures RBAC

`RoleController`/`PermissionController` gataient deja `save`/`delete` via `requireRbacManage`
(helper existant), mais pas `findById`/`findAll` - violation litterale de la regle CLAUDE.md "toute
route qui lit ou modifie des donnees RBAC... y compris les endpoints de lecture (GET)". Corrige en
appliquant le meme helper existant aux lectures, sans aucun changement de service (Role/Permission
n'ont pas de champ organizationId a filtrer - confirme par lecture directe des deux entites de
domaine et par l'usage de `roleService.findByCode("ADMINISTRATEUR")` au bootstrap de chaque nouveau
tenant, qui doit trouver le meme role template quelle que soit l'organisation).

### UserRoleAssignment : meme classe de bug que le contournement GLOBAL de Sale.create (5b-2b), evitee des la conception

`UserRoleAssignment` porte reellement `organizationId` (ajoute en 5b-1), contrairement a Role/
Permission. Gater ses lectures uniquement sur `requireRbacManage` aurait reproduit exactement le
contournement GLOBAL trouve en 5b-2b : un appelant avec RBAC_MANAGE/GLOBAL dans son organisation A
aurait pu lire l'affectation d'une organisation B, puisque `hasPermission` ne valide jamais que la
ligne demandee appartient a l'organisation de l'appelant. Le garde-fou d'appartenance (nouveau
`findById(Long, Long organizationId)`/`findAllByUser(Long, Long organizationId)`) s'execute donc EN
PREMIER dans le controleur, avant `requireRbacManage` - meme ordre que `requireSiteInOrganization`
avant `hasPermission` en 5b-2b, explicitement demande pour eviter toute reintroduction de cette
classe de bug par une inversion d'ordre. Test dedie : un appelant GLOBAL RBAC_MANAGE dans son
organisation ne peut pas lire l'affectation bootstrap d'une AUTRE organisation (404 masque, pas 400
ACCESS_DENIED - preuve que c'est bien le garde-fou d'appartenance qui rejette).

### User.updatePhoto : meme IDOR que changePassword avant Phase 5a, corrige avec le meme motif

`POST /users/{id}/photo` n'avait aucune verification de propriete - n'importe quel utilisateur
authentifie pouvait definir la photo de n'importe quel autre. Meme classe et meme gravite que l'IDOR
sur changePassword corrige en Phase 5a. Decision explicite avant implementation : inclus dans ce
perimetre plutot que catalogue en backlog (contrairement a InventoryController.receive/issue/correct
en 5b-2b) - fix mirror exact de `requireSelf` (nouveau helper `requireSelfForPhoto`, nouveau code
`USER_UPDATE_PHOTO_FORBIDDEN`).

### Root cause additionnelle : User.idEntreprise porte une contrainte FK reelle malgre le commentaire "reference faible"

Le javadoc de `User`/`Tenant` decrit `idEntreprise` comme une reference faible (`Long`, pas
`@ManyToOne`) - exact cote JPA, mais **une contrainte FK reelle existe en base**
(`fk1lqyf8cuumbj0iku4axqklfu3`, `utilisateur.identreprise -> entreprise(id)`,
`V1__initial_schema.sql`). Trouve en ecrivant les tests de scoping User : un id de tenant fabrique
(non lie a une vraie ligne `entreprise`) est rejete a l'insertion par
`DataIntegrityViolationException`. Corrige en creant de vrais `Tenant` via `TenantService.save()`
dans les tests - meme lecon que `AuthorizationServiceIntegrationTest` en Phase 5b-1.

### Root cause additionnelle trouvee en verifiant : meme fixture "organisation deconnectee du principal" qu'en 5b-2b, sur un test preexistant

`OrganizationControllersHttpIntegrationTest.organizationDeleteWithDependentCityIsRejected` (test
preexistant, pas ajoute dans cet increment) creait une organisation fraiche via
`POST /organizations/create` (toujours sans aucune verification) pour y construire une ville, plutot
que d'utiliser l'organisation propre de l'appelant. `delete` etant desormais self-only, la requete
est rejetee en 404 (masquage) avant meme d'atteindre la regle metier `ORGANIZATION_ALREADY_IN_USE`
attendue par le test. Meme cause racine et meme correctif que documente en 5b-2b §3 : reutilise
`organizationIdFromToken` (deja ajoute dans ce meme fichier en 5b-2b) au lieu de creer une
organisation separee.

### Deux arbitrages tranches avant implementation (voir docs/phase-5b2c-report.md §1)

`Organization.save()`/`Tenant.save()` restent non touches (coherent avec les 5 increments 5b-2
precedents : jamais de restriction sur "qui peut creer un nouvel agregat racine", gap RBAC-on-create
deja catalogue). `User.updatePhoto` durci maintenant plutot que reporte (voir ci-dessus).

## Phase 5b-2d (InventoryController writes + SiteController.save City-ownership) : trouve pendant l'increment

Voir `docs/phase-5b2d-report.md` pour le detail complet. Increment supplementaire propose et
approuve apres 5b-2c (pas pre-nomme dans le plan d'origine), sur deux items du backlog deja
catalogues en 5b-2b §4 : `InventoryController.receive/issue/correct` (ecriture, plus severe que le
gap de lecture deja ferme) et `SiteController.save()` (appartenance de City).

### Nouvelles permissions = admins de tenants existants bloques sans migration de seed

`ADMINISTRATEUR` (role bootstrap de chaque tenant) ne regroupe que les codes de permission
explicitement seedes par migration (V3/V4) - pas une notion "toutes permissions" dynamique.
Introduire STOCK_RECEIVE/STOCK_ISSUE/STOCK_CORRECT sans les ajouter a ADMINISTRATEUR aurait
immediatement bloque tout admin de tenant, existant ou nouveau, sur ces trois endpoints -
regression fonctionnelle, pas seulement un durcissement. Corrige par une nouvelle migration
(`V7__seed_stock_adjustment_permissions.sql`, meme motif idempotent que V3/V4), jamais en
modifiant un fichier V* existant (zone gelee CLAUDE.md). A retenir pour tout futur increment qui
introduirait de nouveaux codes de permission : verifier systematiquement si ADMINISTRATEUR doit
etre mis a jour par une migration dediee.

### Root cause additionnelle : /utilisateurs/create (legacy) ne force jamais idEntreprise depuis l'appelant

Trouve en ecrivant un test de regression (`inventoryWritesRequirePermissionEvenOnOwnSite`) : un
utilisateur cree via l'endpoint legacy sans fournir explicitement `entreprise.id` dans le payload
n'appartient a AUCUN tenant - son organizationId ne se resout jamais correctement au login. Meme
gap deja connu et delibere que celui documente en Phase 4a pour `/utilisateurs/*`/`/users/*`
("pas une amelioration deliberee") - non corrige cote production (legacy, hors perimetre), la
fixture de test a simplement ete corrigee pour fournir le champ explicitement.

### Root cause additionnelle trouvee en verifiant : troisieme occurrence de la meme fixture "organisation deconnectee du principal"

`OrganizationControllersHttpIntegrationTest.warehouseCreationRejectsSiteTypeSpoofedInPayload`
(test preexistant) creait une organisation fraiche via `POST /organizations/create` pour y
construire sa hierarchie City/Site de test. `SiteController.save()` verifiant desormais
l'appartenance de la City, la creation du site echouait avant meme d'atteindre le scenario teste.
Troisieme occurrence exacte du meme correctif documente en 5b-2b §3 et 5b-2c §3 : reutilise
`organizationIdFromToken` au lieu de creer une organisation separee. Cette recurrence confirme que
`OrganizationController.save()` (toujours sans aucune verification) est une source structurelle de
regressions de fixture pour tout increment futur qui ajoute une verification d'appartenance en
aval - argument de plus pour le traiter en increment dedie plutot que de continuer a le contourner
au cas par cas dans les tests.
