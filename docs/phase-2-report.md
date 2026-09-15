# Phase 2 — Rapport de sortie de phase

ArchUnit. Traduit en tests qui cassent le build les regles d'architecture
deja enoncees dans CLAUDE.md ("Regles d'architecture — zero tolerance").
Aucune violation trouvee n'a ete corrigee : c'est le perimetre explicite de
Phase 3.

## 1. Ce qui a ete livre

### 1.1 Dependance

`pom.xml` : `com.tngtech.archunit:archunit-junit5:1.3.1` (scope `test`),
version pinnee sur celle deja resolue transitivement via
`spring-modulith-core` (Spring Modulith est lui-meme construit sur
ArchUnit) — evite tout conflit de version dans l'arbre de dependances.

### 1.2 Factorisation du decoupage module/legacy avec Phase 1

Extrait de `ModularityTests` vers
`src/test/java/.../architecture/ModuleBoundaries.java` : `LEGACY_FLAT_PACKAGES`
(les 10 packages legacy plats) et le predicat `IN_LEGACY_FLAT_PACKAGE`.
`ModularityTests` a ete mis a jour pour reference ce predicat partage au
lieu d'en garder une copie privee — comportement strictement identique
(verifie par re-execution, memes 4 cycles + 6 violations qu'avant le
refactor). Justification du partage : `ApplicationModules.of(Class,
DescribedPredicate)` (Spring Modulith) accepte directement un
`com.tngtech.archunit.base.DescribedPredicate` (Spring Modulith est
construit sur ArchUnit) — le meme objet predicat s'utilise tel quel dans
les deux mecanismes de verification, sans aucune traduction.

### 1.3 `ArchitectureRulesTest` — 6 regles, chacune en `@ArchTest` distinct et nomme

`src/test/java/.../architecture/ArchitectureRulesTest.java`, via
`@AnalyzeClasses(packagesOf = ApiGestionDeStockApplication.class,
importOptions = ImportOption.DoNotIncludeTests.class)` :

| # | Regle (`@ArchTest`) | Source CLAUDE.md | Perimetre |
|---|---|---|---|
| 1 | `domain_must_not_depend_on_spring_framework` | "domain/ n'importe jamais Spring" | `..domain..`, exclut `org.springframework.modulith..` (voir §1.4) |
| 2 | `domain_must_not_depend_on_jakarta_persistence` | "domain/ n'importe jamais... jakarta.persistence" | `..domain..` |
| 3 | `no_field_injection_anywhere` | "Injection par constructeur uniquement. Jamais @Autowired sur un champ." | tout le code de production (pas de restriction module/legacy dans CLAUDE.md) |
| 4 | `application_and_domain_must_not_use_security_context_holder` | "Ne jamais appeler SecurityContextHolder depuis application/ ou domain/" | `..application..`, `..domain..` |
| 5 | `application_and_domain_must_not_throw_generic_exceptions` | "Erreurs metier via les exceptions dediees... jamais une RuntimeException generique" | `..application..`, `..domain..` (regle formulee par CLAUDE.md pour "les modules refactores" specifiquement) |
| 6 | `modules_must_be_free_of_cycles` | definition implicite d'un "module" ; duplique volontairement une partie de `ModularityTests` avec l'API ArchUnit "slices" pure | les 8 modules neufs uniquement (legacy exclu via `ModuleBoundaries`, sinon meme bruit qu'en Phase 1) |

Regles 1, 2 sont deux `@ArchTest` distincts plutot qu'un seul combinant
Spring+JPA (CLAUDE.md les cite dans la meme phrase) : separes pour que le
rapport de violation dise precisement laquelle des deux dependances
interdites est en cause — un manquement possible sans l'autre.

Regles 3 et 5 reutilisent les regles prêtes-a-l'emploi d'ArchUnit
(`GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION` et
`GeneralCodingRules.THROW_GENERIC_EXCEPTIONS`) plutot que d'en re-ecrire des
equivalents maison.

**Regle non retenue** : "Un service = un cas d'usage dans les modules
refactores, pas un XxxService fourre-tout" (CLAUDE.md, section
Conventions) — jugee non directement encodable en ArchUnit sans heuristique
fragile (distinguer un "cas d'usage" d'un "fourre-tout" n'est pas une
propriete structurelle testable ; le nombre de methodes publiques d'une
interface n'est pas un proxy fiable). Non implementee, signalee ici pour
que la revue humaine tranche si une regle plus mecanique (ex. nombre max de
methodes par interface `application`) est souhaitee malgre tout.

### 1.4 Ajustement de la regle 1 (Spring) en cours d'ecriture

Le premier run de la regle "domain ne doit pas dependre de Spring" faisait
remonter 4 violations, toutes causees par `@org.springframework.modulith.NamedInterface`
— l'annotation posee en Phase 1 sur les `package-info.java` de
`catalog/identity/inventory/organization.domain.model`. Cette annotation
decrit l'architecture d'un module (une metadonnee de documentation), elle
ne couple le domaine a aucun comportement runtime Spring (pas d'injection,
pas de proxy, pas de contexte applicatif) — contrairement a
`org.springframework.beans`/`context`/`data`/etc. La regle exclut donc
explicitement `org.springframework.modulith..` (voir le commentaire dans
`ArchitectureRulesTest.java`), sans quoi elle aurait remonte un faux
positif cause par le travail de Phase 1 lui-meme plutot qu'un vrai
manquement du code de production. Apres cet ajustement, **la regle passe
au vert** : le domaine des 8 modules est reellement libre de toute
dependance au framework Spring — seule la regle jakarta.persistence
echoue (voir §2).

## 2. Resultat brut de la suite (etat actuel du code, rien corrige)

```
ArchitectureRulesTest : 6 regles, 3 vertes, 3 rouges
```

| Regle | Resultat | Violations |
|---|---|---|
| `domain_must_not_depend_on_spring_framework` | VERT | 0 |
| `domain_must_not_depend_on_jakarta_persistence` | **ROUGE** | 272 |
| `no_field_injection_anywhere` | **ROUGE** | 16 |
| `application_and_domain_must_not_use_security_context_holder` | VERT | 0 |
| `application_and_domain_must_not_throw_generic_exceptions` | VERT | 0 |
| `modules_must_be_free_of_cycles` | **ROUGE** | 4 cycles |

### 2.1 `domain_must_not_depend_on_jakarta_persistence` — 272 violations, 21/21 entites

**Toutes** les entites de domaine des 8 modules (21 sur 21, une par
agregat) portent des annotations `jakarta.persistence` directement :

```
82 Field  | @Column           26 Field | @FetchType (attribut d'annotation)
25 Field  | @JoinColumn       25 Field | @ForeignKey (attribut)
25 Field  | @ConstraintMode   23 Field | @ManyToOne
21 Class  | @Table            21 Class | @Entity
 7 Field  | @EnumType          7 Field | @Enumerated
 4 Field  | @Embedded          1 Field | @Version
 1 Field  | @OneToOne          1 Field | @OneToMany
 1 Field  | @ManyToMany        1 Field | @JoinTable
 1 Class  | @UniqueConstraint
```

Classes concernees : `catalog.{Article,Category}`,
`identity.{Permission,Role,UserRoleAssignment}`,
`inventory.{Stock,StockMovement}`,
`organization.{City,Organization,Site,Warehouse}`,
`purchasing.{PurchaseOrder,PurchaseOrderLine,Supplier}`,
`sales.{Customer,CustomerOrder,CustomerOrderLine,Sale,SaleLine}`,
`transfers.{StockTransfer,StockTransferLine}`.

Detail complet et piste de correction proposee (non implementee) :
`docs/migration-notes.md`, "Trouve pendant Phase 2", section 1.

### 2.2 `no_field_injection_anywhere` — 16 violations, 0 dans les modules neufs

Toutes dans le legacy/l'infrastructure transverse :
`config.ApplicationRequestFilter` (2), `config.FlickrConfiguration` (4),
`controller.AuthenticationController` (3),
`services.auth.ApplicationUserDetailsService` (2),
`services.impl.FlickrServiceImpl` (4), `utils.JwtUtil` (1). Detail complet
dans `docs/migration-notes.md`.

### 2.3 `modules_must_be_free_of_cycles` — 4 cycles, deja connus (Phase 1)

Exactement les 4 memes cycles que `ModularityTests.verifiesModularStructure`
avait deja detectes en Phase 1, meme cause racine unique
(`catalog.application.impl.ArticleServiceImpl` accedant directement aux
internes de `purchasing`/`sales`). **Ce n'est pas un nouveau finding** : deux
mecanismes independants (Spring Modulith et ArchUnit "slices" pure)
s'accordent parfaitement sur ce point, ce qui renforce la confiance dans le
diagnostic. Rien de nouveau a logguer dans migration-notes.md pour ce point
— deja fait en Phase 1.

## 3. Etat de `./mvnw clean verify`

```
Tests run: 124, Failures: 3, Errors: 1, Skipped: 0
BUILD FAILURE
```

- 120 tests verts (les 117 de Phase 0/1 inchanges + 3 regles ArchUnit qui
  passent).
- 1 erreur : `ModularityTests.verifiesModularStructure` — **deja rouge
  depuis la Phase 1**, cause inchangee (cycle catalog/purchasing/sales).
- 3 echecs : les 3 regles `ArchitectureRulesTest` du tableau §2.

**ArchUnit ajoute deux causes d'echec reellement nouvelles**
(`domain_must_not_depend_on_jakarta_persistence` et
`no_field_injection_anywhere`) **et confirme, sans en ajouter une nouvelle,
la cause deja connue de Phase 1** (le cycle catalog/purchasing/sales, via
`modules_must_be_free_of_cycles`). Le decompte total de causes d'echec
distinctes passe donc de 1 (Phase 1) a 3 (Phase 2) : la violation JPA et la
violation d'injection par champ sont neuves ; le cycle est une confirmation,
pas un ajout.

## 4. Zones d'ombre / decisions prises en cours de route

1. **Regle Spring ajustee pour exclure `org.springframework.modulith`**
   (§1.4) — decision prise en ecrivant la regle, pas une ambiguite
   bloquante, mais signalee car elle change le resultat brut initial (4
   violations -> 0). Sans cet ajustement, la Phase 1 se serait
   retrospectivement "auto-incriminee".
2. **Regle "un service = un cas d'usage" non implementee** (§1.3) — jugee
   trop heuristique pour un test qui casse le build sans faux positifs.
   Signalee pour arbitrage si la revue humaine juge qu'une version plus
   mecanique vaut la peine.
3. **`no_field_injection_anywhere` couvre plus que le seul `@Autowired`
   cite par CLAUDE.md** (aussi `@Value`/`@Inject`/`@Resource`, via la regle
   prete-a-l'emploi ArchUnit) — assume comme fidele a l'esprit de la regle
   ("injection par constructeur uniquement"), signale pour validation
   explicite si un perimetre plus etroit (uniquement `@Autowired`) etait
   en fait voulu.

## 5. Prochaine etape

Phase 2 terminee. **Ne pas enchainer sur la Phase 3 sans feu vert
explicite** — Phase 3 sera la premiere a corriger reellement du code de
production (la violation ArticleServiceImpl de Phase 1, la separation
JPA/domaine de Phase 2, l'injection par champ de Phase 2) et merite la
revue la plus attentive de toute la migration jusqu'ici.
