# Phase 1 — Rapport de sortie de phase

Spring Modulith. Formalise les frontieres de module qui existent deja dans
les faits (les 8 modules listes dans CLAUDE.md) et les rend verifiables par
un test. Aucun code deplace entre packages, aucune violation "corrigee" :
c'est le perimetre explicite de Phase 3.

## 1. Ce qui a ete livre

### 1.1 Dependances

- `pom.xml` : import du BOM `spring-modulith-bom:1.2.13` (ligne alignee sur
  Spring Boot 3.3.x — ne pas monter en 1.3+/2.x sans monter Boot en meme
  temps), ajout de `spring-modulith-starter-core` et
  `spring-modulith-starter-test`.
- **Ecart avec la demande initiale** ("scope test" pour les deux) : au fil
  du point 4 (voir §4), appliquer `@NamedInterface` sur des
  `package-info.java` de `src/main/java` a impose de sortir
  `spring-modulith-starter-core` du scope `test` (il doit etre resolu a la
  compilation du code de production, pas seulement des tests).
  `spring-modulith-starter-test` reste scope `test` (JUnit 5 uniquement).
  Deviation mineure, documentee ici pour validation explicite.

### 1.2 `ModularityTests`

`src/test/java/com/kfokam48/gestiondestock/ModularityTests.java` avec :
- `verifiesModularStructure()` — `ApplicationModules.of(...).verify()`.
- `createModuleDocumentation()` — genere les diagrammes PlantUML sous
  `target/spring-modulith-docs/` (non commite, regenerable a la demande via
  `./mvnw test -Dtest=ModularityTests#createModuleDocumentation`, comme deja
  documente dans CLAUDE.md).

**Decision de perimetre a valider** : Spring Modulith traite par defaut
CHAQUE sous-package direct du package de base
(`com.kfokam48.gestiondestock`) comme un module. Or ce package contient, a
cote des 8 modules "neufs", **dix packages legacy plats** (`model`,
`services`, `dto`, `controller`, `validator`, `repository`, `exception`,
`handlers`, `interceptor`, `config`, `utils`) qui n'ont jamais ete concus
comme des modules — ce sont les adaptateurs de compatibilite en cours
d'extinction que CLAUDE.md decrit deja. Un run sans exclusion les traite
comme des modules a part entiere, ce qui produit un resultat domine par du
bruit sans rapport avec la question posee (detail complet en §2.1).
`verifiesModularStructure()` exclut donc ces dix packages via un
`DescribedPredicate` explicite et commente dans le code — **ce n'est pas
une correction de violation** (aucun `@Disabled`, aucune assertion
supprimee), c'est le choix de ce qui compte comme "module" pour cette
verification, conforme a la propre definition de CLAUDE.md. Si ce choix ne
convient pas, le run brut (sans exclusion) est integralement conserve en
§2.1 pour trancher autrement.

### 1.3 `@NamedInterface` appliques (12 packages, changement non structurel)

Conformement au feu vert explicite ("ajouter l'annotation elle-meme, sans
deplacement de code, est autorise sans validation prealable"), 12
`package-info.java` ont ete crees pour exposer exactement les types que
d'autres modules "neufs" utilisent deja legitimement (identifie a partir
des violations "depends on non-exposed type" du run scope, voir §2.2) :

| Module | Package annote | Types exposes utilises ailleurs |
|---|---|---|
| catalog | `application.dto` | `ArticleDto` (inventory, purchasing, sales, transfers) |
| catalog | `domain.model` | `Article` — relation JPA `@ManyToOne` (inventory.Stock/StockMovement, purchasing.PurchaseOrderLine, sales.SaleLine/CustomerOrderLine) |
| organization | `application.dto` | `SiteDto`, `OrganizationDto` (catalog, inventory, purchasing, sales, transfers, reporting) |
| organization | `domain.model` | `Site`, `Organization` — relations JPA |
| organization | `application` | `SiteService` (reporting) |
| identity | `application` | `AuthorizationService` (purchasing, sales, transfers, reporting) |
| identity | `domain.model` | `ScopeType` (idem) |
| inventory | `application` | `InventoryFacade` (purchasing, sales, transfers, reporting) |
| inventory | `application.dto` | `StockDto`, `StockMovementDto` (reporting) |
| inventory | `domain.model` | `StockMovementSource` (purchasing, sales) |
| sales | `application` | `SaleService` (reporting) |
| sales | `application.dto` | `SaleDto`, `SaleLineDto` (reporting) |

**Deliberement non expose** : `purchasing.domain.model`,
`purchasing.infrastructure.persistence`, `sales.domain.model`,
`sales.infrastructure.persistence` — c'est precisement ce que
`catalog.application.impl.ArticleServiceImpl` n'aurait jamais du toucher
(voir §3). Les exposer aurait fait disparaitre le signal au lieu de le
corriger.

**Proposes mais non appliques**, faute de consommateur actuel parmi les 8
modules (voir `docs/migration-notes.md`) : `purchasing.application`/
`application.dto`, tout `transfers.*`, tout `reporting.*`.

## 2. Resultat de `modules.verify()`

### 2.1 Run brut (sans exclusion des packages legacy) — reference complete

`ApplicationModules.of(ApiGestionDeStockApplication.class).verify()`, zero
predicat, sur l'etat du code **avant** application des `@NamedInterface`
(§1.3) :

```
Tests run: 1, Failures: 0, Errors: 1
2077 lignes de violation ("- ...") sur 8611 lignes de sortie.
65 blocs "Cycle detected" distincts.
```

Sur ces 65 cycles, **61 impliquent au moins un des packages legacy plats**
(`dto`, `model`, `repository` apparaissant comme "Slice" au meme titre que
`catalog`/`sales`/etc.), typiquement de la forme :

```
Cycle detected: Slice catalog -> Slice dto -> Slice catalog
Cycle detected: Slice catalog -> Slice organization -> Slice model -> Slice catalog
Cycle detected: Slice catalog -> Slice purchasing -> Slice identity -> Slice model -> Slice catalog
...
```

Cause : les DTO/entites legacy (`dto.LigneVenteDto`, `model.EtatCommande`,
etc.) referencent des types des modules neufs (ex.
`catalog.application.dto.ArticleDto`), tandis que les adaptateurs legacy
(`services.impl.*ServiceImpl`) appellent en retour les services des modules
neufs — cree un aller-retour "module neuf <-> package legacy" que Spring
Modulith rapporte comme un cycle, alors qu'il ne s'agit que du pattern
adaptateur documente en Phase 0/16-23 (le legacy delegue au module neuf).
**Ce n'est pas une violation d'architecture au sens ou l'entend CLAUDE.md**
(le legacy n'est pas un module), d'ou le choix d'exclusion en §1.2.

Log complet archive (non commite, trop volumineux) — reproductible via
`./mvnw test -Dtest=ModularityTests#verifiesModularStructure` apres avoir
temporairement retire le predicat d'exclusion dans `ModularityTests.java`.

### 2.2 Run scope aux 8 modules, avant `@NamedInterface`

Meme code applicatif, mais `ApplicationModules.of(..., excludeLegacyFlatPackages)` :

```
Tests run: 1, Failures: 0, Errors: 1
2181 lignes de sortie.
4 cycles distincts + 43 violations "depends on non-exposed type".
```

Les 4 cycles :
```
Slice catalog -> Slice purchasing -> Slice catalog
Slice catalog -> Slice purchasing -> Slice inventory -> Slice catalog
Slice catalog -> Slice sales -> Slice catalog
Slice catalog -> Slice sales -> Slice inventory -> Slice catalog
```

Les 43 violations "non-exposed type" correspondent exactement aux 12
paires module/package listees en §1.3 (avant qu'elles ne soient exposees) —
c'est ce qui a guide la liste des `@NamedInterface` a appliquer.

### 2.3 Run final (apres application des 12 `@NamedInterface`) — etat actuel du depot

```
Tests run: 1, Failures: 0, Errors: 1
554 lignes de sortie.
```

Toutes les violations "non-exposed type" sur les 12 paires legitimes ont
disparu. **Il ne reste que 4 cycles + 6 violations "non-exposed type"**,
et les deux categories ont exactement la meme cause racine unique :

```
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderLine within module 'purchasing'!
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.PurchaseOrderLineRepository within module 'purchasing'!
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderLine within module 'sales'!
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.sales.domain.model.SaleLine within module 'sales'!
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderLineRepository within module 'sales'!
- Module 'catalog' depends on non-exposed type
  com.kfokam48.gestiondestock.sales.infrastructure.persistence.SaleLineRepository within module 'sales'!
```

Voir §3 pour la cause racine precise et `docs/migration-notes.md` pour la
piste de correction proposee en Phase 3. **`ModularityTests.verifiesModularStructure()`
echoue donc actuellement, intentionnellement, et c'est le resultat attendu
de cette phase** (voir §5).

## 3. Cause racine de la seule violation reelle restante

`catalog.application.impl.ArticleServiceImpl` (methodes
`findHistoriqueVentes`, `findHistoriaueCommandeClient`,
`findHistoriqueCommandeFournisseur`, `delete`) injecte directement :
- `purchasing.infrastructure.persistence.PurchaseOrderLineRepository`
- `sales.infrastructure.persistence.SaleLineRepository`
- `sales.infrastructure.persistence.CustomerOrderLineRepository`

et manipule directement `purchasing.domain.model.PurchaseOrderLine`,
`sales.domain.model.SaleLine`, `sales.domain.model.CustomerOrderLine` —
violation directe de la regle CLAUDE.md "Un module n'accede jamais au
repository ou a l'entite JPA d'un autre module". C'est la SEULE cause
racine de la totalite des violations reelles trouvees entre les 8 modules
neufs. Le detail complet (chaque appel de methode, chaque champ) est dans
`docs/migration-notes.md`, section "Trouve pendant Phase 1", avec une piste
de correction proposee pour Phase 3 (non implementee ici).

## 4. `@NamedInterface` — recapitulatif

Voir §1.3 pour le tableau complet. Resume : 12 packages annotes (8 modules
producteurs sur 4 axes : `application`, `application.dto`, `domain.model`),
tous justifies par un consommateur reel identifie dans le run scope avant
annotation (§2.2). 3 groupes de packages identifies comme candidats futurs
mais non appliques faute de consommateur actuel — voir
`docs/migration-notes.md`.

## 5. Etat de `./mvnw clean verify`

**Rouge, intentionnellement**, a cause de
`ModularityTests.verifiesModularStructure()` qui echoue sur la violation
decrite en §3. Ce nouveau test n'a pas ete desactive, contourne, ni retire
du run par defaut (CLAUDE.md interdit explicitement le `@Disabled` pour
faire passer un build) : il rend visible, de maniere permanente et
automatisee, une violation qui existait deja silencieusement avant cette
phase. Tous les autres tests (116 de Phase 0 + tests preexistants) restent
verts — seul `ModularityTests#verifiesModularStructure` echoue. Cet etat
restera rouge jusqu'a la correction en Phase 3 (deplacement des methodes
"historique" hors de `catalog`, voir migration-notes.md), sauf decision
contraire explicite.

`ModularityTests#createModuleDocumentation` passe (genere les diagrammes,
n'appelle pas `verify()`).

## 6. Zones d'ombre / decisions a valider

1. **Perimetre d'`ApplicationModules.of(...)`** (§1.2) : exclusion des 10
   packages legacy plats. Judgment call assume et documente, pas une
   ambiguite bloquante — mais c'est une decision de conception qui merite
   validation explicite avant Phase 2 (ArchUnit reutilisera probablement le
   meme decoupage module/legacy).
2. **Scope `spring-modulith-starter-core`** (§1.1) : promu de `test` a
   compile par necessite technique (voir §1.1). Deviation mineure de la
   consigne initiale, signalee pour validation.
3. **`ModularityTests` fait echouer `./mvnw clean verify`** (§5) : assume,
   car c'est litteralement l'objectif de la phase ("rapporter le resultat
   brut, sans le corriger"), mais rompt avec la garantie "repo toujours
   vert" appliquee en Phase 0. A confirmer que c'est le comportement voulu
   avant de considerer la Phase 1 comme definitivement close.

## 7. Prochaine etape

Phase 1 terminee. **Ne pas enchainer sur la Phase 2 (ArchUnit) sans feu
vert explicite**, conformement a la regle de travail.
