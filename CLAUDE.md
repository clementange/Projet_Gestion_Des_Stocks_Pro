# CLAUDE.md

Instructions persistantes pour Claude Code sur ce dépôt. **Suivi par git depuis la Phase 5g** : il
vivait auparavant un niveau au-dessus de la racine du dépôt, donc hors de tout commit — ses mises
à jour étaient invisibles pour qui clone le projet ou relit l'historique. Voir `docs/ARCHITECTURE.md` pour le détail complet de l'architecture (diagrammes C4/UML, séquences) — à lire à la demande quand tu travailles sur du code cross-module ; il n'est pas chargé automatiquement ici pour économiser le contexte.

## Projet

API de gestion de stock multi-site (Spring Boot 3.3.5, Java 17, PostgreSQL, JPA/Hibernate, Spring Security + JWT). Monolithe modulaire **en cours de migration** d'un legacy en couches vers une Clean Architecture par modules métier (`catalog`, `identity`, `inventory`, `organization`, `purchasing`, `sales`, `transfers`, `reporting`).

## Commandes

- Build + tests : `./mvnw clean verify`
- Un seul test : `./mvnw test -Dtest=NomDeLaClasse#methode`
- Lancer l'app en local : `./mvnw spring-boot:run` (nécessite `.env` copié depuis `.env.example`, Postgres démarré)
- Vérifier uniquement les règles d'architecture : `./mvnw test -Dtest=ArchitectureRulesTest,ModularityTests`
- Générer les diagrammes de modules à jour : `./mvnw test -Dtest=ModularityTests#createModuleDocumentation`

**Toujours faire tourner `ArchitectureRulesTest` et `ModularityTests` avant de proposer un diff qui touche `src/main/java`.** Un changement qui les fait passer au rouge doit être corrigé, jamais contourné (pas de `@Disabled`, pas de suppression de règle pour faire passer un build).

## Règles d'architecture — zéro tolérance

Ces règles sont vérifiées par ArchUnit/Spring Modulith, pas seulement documentées : tout code qui les viole ne doit pas être proposé.

- **Structure par module** : `domain/` (model, event, port) → `application/` (usecase, dto, port) → `adapter/` (in/web, out/persistence, out/*) → `config/`. Respecte cette structure pour tout module neuf ou refactoré.
- **`domain/` n'importe jamais Spring ni `jakarta.persistence`.** Les invariants métier (ex. `Stock.issue()`, `Stock.reserve()`, `PurchaseOrder.requireReceivable()`) vivent dans les entités de domaine, pas dans les services.
- **Un module n'accède jamais au repository ou à l'entité JPA d'un autre module.** Seule son interface publique (`InventoryFacade` et équivalents `@NamedInterface`) est appelable depuis l'extérieur.
- **Injection par constructeur uniquement.** Jamais `@Autowired` sur un champ.
- **Ne jamais appeler `SecurityContextHolder` depuis `application/` ou `domain/`.** L'identité (`userId`, `idEntreprise`) est résolue dans l'adapter web (`@AuthenticationPrincipal ExtendedUser`) et passée en paramètre.
- **Les cinq paquets plats legacy (`model/`, `dto/`, `repository/`, `services/`, `controller/`) ont été supprimés en Phase 4e et ne doivent jamais revenir** — la règle ArchUnit `the_flat_legacy_packages_must_never_come_back` le vérifie. Toute classe neuve va dans son module métier. Deux destinations transverses existent : `shared/` pour ce qui est utilisé par tous les modules (`AbstractEntity`, `Adresse`, `AdresseDto`, `ExtendedUser`), et `legacy/` **uniquement** pour les données antérieures à la migration (tables plates `lignevente`/`lignecommandeclient`/`lignecommandefournisseur` servies par `/articles/historique/**`) — ce module ne doit pas grossir.
- Quand un module a besoin de `legacy` (ou de tout module qui dépend déjà de lui), **inverser la dépendance** via un port que le module demandeur déclare (`catalog.domain.port.ArticleUsagePort`), jamais par un appel direct qui formerait un cycle.
- **Les DTO de commande/lecture applicatifs sont des `record`**, pas des classes Lombok mutables, sauf contrainte de compatibilité JSON legacy explicite.

## Sécurité — zéro tolérance

- Jamais de secret, mot de passe ou clé API en dur dans le code ou dans `application.yml` : toujours `${VAR_ENV:...}` avec la variable documentée dans `.env.example`.
- Toute génération de mot de passe/token doit utiliser `SecureRandom` — jamais de valeur constante retournée par une méthode nommée "random"/"generate".
- Toute route qui lit ou modifie des données RBAC (`UserRoleAssignment`, `Role`, `Permission`) doit vérifier une permission via `AuthorizationService.hasPermission(...)`, y compris les endpoints de lecture (`GET`).
- **Une permission de portée `GLOBAL` signifie « toute mon organisation », jamais « toutes les organisations ».** Elle décide du DROIT de lire, jamais du PÉRIMÈTRE lu : le filtre d'organisation reste obligatoire en plus. C'est la confusion qui a produit la fuite complète de `/reporting/stock/global` (Phase 5g).
- **Le périmètre d'organisation se filtre en SQL, pas en Java après un `findAll()`.** Un filtre en aval est une protection qu'on peut oublier d'appliquer — c'est exactement ce qui est arrivé en 5g. Toute requête de liste prend `organizationId` en paramètre de requête.
- **Ordre imposé sur toute écriture : appartenance → permission → écriture.** L'appartenance renvoie un `*_NOT_FOUND` masqué, la permission un `*_ACCESS_DENIED`. Ne jamais déduire l'isolation inter-tenant de `hasPermission` : une affectation de portée `GLOBAL` matche n'importe quel `scopeId`.
- **Toute nouvelle permission exige une migration Flyway** qui l'insère *et* l'accorde à `ADMINISTRATEUR` (`INSERT ... ON CONFLICT DO NOTHING`), sinon elle est inobtenable et l'endpoint devient définitivement bloqué. Voir `docs/rbac-matrix.md` pour la procédure complète et la matrice ressource × action × portée.
- **Un `create` n'est jamais un `update`** : tout endpoint de création appelle `CreationIdGuard.requireNoId(dto.getId(), ...)`. Un `save()` avec un id client est un MERGE JPA, donc une prise de contrôle de ressource.
- Ne jamais faire confiance à un objet imbriqué fourni par le client (`dto.getSite()`, `dto.getCity()`) : recharger l'entité depuis la base avant de vérifier son appartenance.
- Ne jamais renvoyer `exception.getMessage()` brut au client pour une exception non métier (500) — utiliser `ProblemDetail`/message générique, logguer le détail côté serveur uniquement.
- CORS : jamais `allowedOriginPatterns("*")` combiné à `allowCredentials(true)`.

## Tests

- Toute correction de bug doit être accompagnée d'un test de non-régression reproduisant le bug avant fix.
- Les entités agrégats (`Stock`, `PurchaseOrder`, `StockTransfer`, `CustomerOrder`) sont testées au niveau domaine pur (pas de `@SpringBootTest` pour tester un invariant métier qui ne dépend pas du contexte Spring).
- Les tests d'intégration touchant la base utilisent Testcontainers PostgreSQL, jamais une base locale supposée déjà démarrée.
- Ne jamais modifier un test existant pour le faire passer sans comprendre pourquoi il échouait — signaler l'incertitude plutôt que d'adapter l'assertion à la sortie observée.

## Conventions de code

- **Java 21** (cible atteinte en L0 ; `java.version=21` dans `pom.xml`). Lombok >= 1.18.46 — les versions < 1.18.34 cassent sous JDK 23+ (`TypeTag :: UNKNOWN`). `maven-enforcer-plugin` exige JDK >= 21 et Maven >= 3.9 (wrapper 3.9.12). **JDK 21 est la chaîne de référence** : sous JDK 25 le build passe mais ArchUnit 1.3.1 ne lit pas les classes du JDK (`falling back to simple import`) et son analyse est dégradée — valider les règles d'architecture sous 21. JaCoCo >= 0.8.16 requis (0.8.12 ne sait pas instrumenter le bytecode Java 25). Pas de nouvelles classes en syntaxe pré-`record`/pré-`switch` expression quand le langage le permet.
- Lombok `@Builder`/`@Data` acceptés sur les entités JPA et DTO legacy ; éviter `@Data` sur les entités de domaine pur des modules refactorés (encapsulation explicite des invariants).
- Les modules neufs utilisent le vocabulaire anglais (`Sale`, `Supplier`, `PurchaseOrder`) ; le vocabulaire métier français (Ventes, Fournisseur, Commande...) reste réservé au legacy.
- Un service = un cas d'usage dans les modules refactorés (`application/usecase/ReceivePurchaseOrderLine`), pas un `PurchaseOrderService` fourre-tout qui grossit indéfiniment.
- Erreurs métier via les exceptions dédiées existantes (`EntityNotFoundException`, `InvalidEntityException`, `InvalidOperationException` + `ErrorCodes`), jamais une `RuntimeException` générique.

## Zones sensibles à ne jamais modifier sans confirmation explicite

- L'isolation multi-tenant repose **entièrement** sur les vérifications d'appartenance explicites (`requireSiteInOrganization`, `findById(id, organizationId)`, …) depuis que `interceptor/Interceptor` a été supprimé en Phase 4e. Il n'y a plus de filet SQL : une lecture ou une écriture qui oublie la vérification d'appartenance fuit, point. `CrossTenantEndpointMatrixTest` est le garde-fou — tout endpoint neuf doit y être sondé ou exclu avec justification.
- Les migrations Flyway déjà appliquées (`src/main/resources/db/migration/V*.sql`) — ne jamais éditer un fichier `V*` existant, toujours en créer un nouveau.
- Les DTO du contrat HTTP de l'historique (`legacy/*Dto.java`) — leur forme JSON est un contrat externe servi par `/articles/historique/**`, tout changement de champ doit être justifié et documenté.

## À consulter à la demande (non chargés automatiquement)

- `docs/ARCHITECTURE.md` — diagrammes C4/UML, flux détaillés, écarts assumés et justifiés.
- `TODO.md` — fonctionnalités restant à faire (peut être désynchronisé de l'état réel du code, vérifier avant de s'y fier).
