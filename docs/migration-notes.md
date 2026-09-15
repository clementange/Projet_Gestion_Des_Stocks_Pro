# Notes de migration

Fichier de suivi des constats qui appartiennent a une phase future de la
migration Clean Architecture, releves en travaillant sur la phase courante
mais volontairement non traites tout de suite. Voir `CLAUDE.md` pour l'ordre
des phases.

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
