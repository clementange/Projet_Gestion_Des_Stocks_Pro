# Phase 5b-2c — cas spéciaux (Organization/Tenant self-only, User via Tenant.id, Role/Permission/UserRoleAssignment) — Rapport

Troisième et dernier incrément de 5b-2 (voir docs/phase-5b1-report.md §8, docs/phase-5b2a-report.md,
docs/phase-5b2b-report.md). Contrairement aux deux précédents, ce n'est pas un seul motif appliqué
à N entités : c'est structurellement quatre motifs différents selon la nature de chaque entité.

## 0. Investigation préalable

Vérifié par lecture directe du code, sans présumer par analogie avec 5b-2a/2b :

- **`Organization`/`Tenant`** : aucun appelant interne cross-module sur `findAll`/`delete`. Mais
  `TenantService.findById(Long)` **est** appelé en interne — `ApplicationUserDetailsService` au
  login (résolution `Tenant.id -> Tenant.organizationId`, le mécanisme même bâti en 5b-1) et
  l'adaptateur legacy `UtilisateurServiceImpl` — variante conservée intacte, nouvelle variante
  self-only ajoutée pour le contrôleur, même motif que `SiteService.findById` en 5b-2b.
- **`User`** : `User.idEntreprise` (Tenant.id) est le seul scalaire disponible pour scoper — PAS
  `organizationId` (absent de l'entité). `findByEmail(String)` est le **mécanisme de login
  lui-même** (`ApplicationUserDetailsService`) : doit rester global, résolu AVANT tout contexte
  tenant. `findById`/`findByEmail`/`findAll`/`delete` sont aussi appelés par l'adaptateur legacy
  `UtilisateurServiceImpl` — conservés intacts, conformément à la posture déjà actée en Phase 4a
  ("pas une amélioration délibérée"). `updatePhoto` n'avait, lui, aucune vérification du tout —
  même classe d'IDOR que `changePassword` avant Phase 5a.
- **`Role`/`Permission`** : **aucun champ `organizationId`**, confirmé par lecture directe des
  deux entités de domaine. Catalogue global partagé entre tous les tenants — confirmé par
  l'usage de `TenantRegistrationServiceImpl.roleService.findByCode("ADMINISTRATEUR")`, qui doit
  trouver le même rôle template pour n'importe quel nouveau tenant. Rien à filtrer par ligne.
  Le vrai gap : `save`/`delete` sont déjà protégés par `requireRbacManage` (helper existant dans
  `RoleController`/`PermissionController`), mais **pas** `findById`/`findAll` — violation directe
  du zero-tolerance CLAUDE.md ("toute route qui lit ou modifie des données RBAC... y compris les
  endpoints de lecture (GET)").
- **`UserRoleAssignment`** : même gap GET que Role/Permission, **plus** un problème plus fin —
  cette entité porte réellement `organizationId` (ajouté en 5b-1). Gater uniquement sur
  `requireRbacManage` reproduirait exactement la classe de bug trouvée en 5b-2b (le contournement
  GLOBAL sur `Sale.create`) : un appelant avec `RBAC_MANAGE`/GLOBAL dans son organisation A
  passerait la vérification de permission puis pourrait lire une affectation appartenant à une
  organisation B, puisque `hasPermission` ne valide jamais que la ligne demandée appartient à
  l'organisation de l'appelant. `ApplicationUserDetailsService.findAllByUser` (login, résolution
  des authorities) doit rester non filtré en interne.

## 1. Deux arbitrages tranchés avant implémentation

- **`Organization.save()`/`Tenant.save()` non touchés.** `POST /organizations/create` et
  l'équivalent Tenant restent sans aucune vérification — cohérent avec le précédent établi sur
  les cinq incréments précédents de 5b-2 (jamais de restriction sur "qui peut créer un nouvel
  agrégat racine", gap RBAC-on-create déjà catalogué et délibérément reporté). Racine identifiée
  du problème de fixture rencontré en 5b-2b §3, reste ouverte, réservée pour un futur increment
  RBAC-on-create dédié si jamais traité.
- **`User.updatePhoto` durci en self-only maintenant.** IDOR de même forme et même gravité que
  `changePassword` (corrigé en Phase 5a) — inclus dans ce périmètre plutôt que catalogué en
  backlog, décision explicite avant implémentation.

## 2. Conception par entité

**Organization** — `findById(Long id, Long callerOrganizationId)` : `id.equals(callerOrganizationId)`,
sinon même `ORGANIZATION_NOT_FOUND` qu'un id inexistant (masquage d'existence, cohérent avec tous
les incréments 5b-2 précédents). `findAll(Long callerOrganizationId)` : filtre à au plus un
élément (le sien). `delete(Long id, Long callerOrganizationId)` : même vérification self-only
avant la règle métier existante (`ORGANIZATION_ALREADY_IN_USE` si des villes existent).

**Tenant** — même schéma que `Site` en 5b-2b : `findById(Long)` conservé intact en interne,
`findById(Long id, Long callerTenantId)` ajouté pour le contrôleur. `findAll`/`delete`/
`updatePhoto` changés directement (aucun appelant interne), tous self-only via
`principal.getIdEntreprise()`. `register` reste public, inchangé.

**User** — `findById(Long)`/`findByEmail(String)`/`findAll()`/`delete(Long)` conservés intacts en
interne (login + adaptateur legacy). Nouvelles variantes `findById(Long, Long callerTenantId)`,
`findByEmail(String, Long callerTenantId)`, `findAll(Long callerTenantId)`,
`delete(Long, Long callerTenantId)` ajoutées pour `UserController`, filtrant sur
`User.idEntreprise` (pas `organizationId`). Mismatch = même `USER_NOT_FOUND` masqué.
`updatePhoto` : aucun changement de service — nouveau helper `requireSelfForPhoto` dans le
contrôleur (mirror exact de `requireSelf` déjà utilisé par `changePassword`), nouveau code
`ErrorCodes.USER_UPDATE_PHOTO_FORBIDDEN`.

**Role/Permission** — aucun changement de service. `requireRbacManage` (helper déjà existant,
déjà utilisé sur `save`/`delete`) appliqué à `findById`/`findAll` dans les deux contrôleurs.
`RoleService.findByCode` n'est pas exposé en HTTP (utilisé uniquement en interne par le bootstrap
tenant) : rien à gater là.

**UserRoleAssignment** — `findAllByUser(Long)` conservé intact en interne (login). Nouveau
`findById(Long id, Long organizationId)` (aucun appelant interne, changé directement) et nouveau
`findAllByUser(Long userId, Long organizationId)`, tous deux filtrant sur
`UserRoleAssignment.organizationId`, mismatch masqué en `USER_ROLE_ASSIGNMENT_NOT_FOUND`. Dans le
contrôleur, **ordre d'exécution critique, explicitement demandé et respecté** : le garde-fou
d'appartenance s'exécute EN PREMIER via l'appel au service scope-organisation, `requireRbacManage`
EN SECOND — exactement l'ordre `requireSiteInOrganization` avant `hasPermission` établi en 5b-2b.
Le garde-fou d'appartenance ne dépend jamais du résultat de `hasPermission` : un appelant GLOBAL
RBAC_MANAGE dans son organisation ne peut pas s'en servir pour contourner l'isolation d'une autre
organisation, quelle que soit l'évolution future de la logique GLOBAL dans `hasPermission`.

## 3. Root cause additionnelle trouvée en vérifiant

`./mvnw clean verify` a révélé 6 échecs après implémentation (sur 227 tests), tous dans des
fixtures de test (3 vraies erreurs d'assertion de ma part, 1 fixture avec la même cause racine
que 5b-2b §3, 3 erreurs de contrainte FK) :

- **`OrganizationModuleIntegrationTest.organizationAccessIsSelfOnly`** et
  **`TenantServiceImplTest.tenantAccessIsSelfOnly`** : erreur d'assertion dans le test lui-même,
  pas dans le code de production — `findAll(orgB.getId())`/`findAll(tenantB.getId())` renvoie
  correctement **1** élément (l'organisation/le tenant de l'appelant lui-même), pas 0 ; mon
  assertion initiale supposait à tort 0. Corrigé en vérifiant que l'unique élément retourné est
  bien celui de l'appelant, jamais celui de l'autre organisation.
- **`OrganizationControllersHttpIntegrationTest.organizationDeleteWithDependentCityIsRejected`**
  (test préexistant, pas ajouté dans cet incrément) : même cause racine que le correctif de
  fixture documenté en 5b-2b §3 — créait une organisation fraîche via `POST /organizations/create`
  pour y construire une ville, distincte de l'organisation propre de l'appelant. `delete` étant
  désormais self-only, la requête est rejetée en 404 (masquage) avant même d'atteindre la règle
  métier `ORGANIZATION_ALREADY_IN_USE` attendue. Corrigé en réutilisant `organizationIdFromToken`
  (déjà ajouté en 5b-2b dans ce même fichier) au lieu de créer une organisation séparée.
- **`UserServiceImplTest`** (3 méthodes) : `DataIntegrityViolationException` sur la contrainte FK
  `fk1lqyf8cuumbj0iku4axqklfu3` (`utilisateur.identreprise -> entreprise(id)`). Découverte
  concrète : bien que le commentaire javadoc de `User`/`Tenant` décrive `idEntreprise` comme une
  "référence faible (pas de `@ManyToOne`)", une contrainte FK réelle existe bel et bien en base
  (`V1__initial_schema.sql`) — un id de tenant fabriqué (non lié à une vraie ligne `entreprise`)
  est rejeté à l'insertion. Corrigé en créant de vrais `Tenant` via `TenantService.save()` dans le
  test plutôt que des identifiants arbitraires — même leçon que
  `AuthorizationServiceIntegrationTest` en Phase 5b-1 (organisations fabriquées non liées à une
  vraie ligne).

## 4. Tests

- `OrganizationModuleIntegrationTest.organizationAccessIsSelfOnly` — self-only read/list/delete.
- `TenantServiceImplTest` (nouveau fichier) — self-only read/list/delete/updatePhoto, plus
  confirmation explicite que `findById(Long)` interne reste non filtré.
- `UserServiceImplTest` (nouveau fichier) — scoping par tenant sur read/findByEmail/list/delete,
  plus confirmation explicite que les variantes internes non filtrées restent fonctionnelles.
- `PhotoAttachmentCharacterizationTest.attachingPhotoToAnotherUserIsRejected` — IDOR
  `updatePhoto` fermé en self-only, mirror direct du test équivalent pour `changePassword`
  (Phase 5a).
- `RbacAdminEndpointsHttpIntegrationTest` (3 nouvelles méthodes) :
  `plainUserCannotReadRolesOrPermissions`, `plainUserCannotReadUserRoleAssignments` — GET
  désormais gaté comme les écritures ; et surtout
  `globalRbacManageCallerCannotReadAssignmentFromAnotherOrganization` — reproduit précisément le
  scénario décrit en §0 (appelant GLOBAL RBAC_MANAGE dans son organisation, tentative de lecture
  d'une affectation d'une AUTRE organisation) et vérifie une réponse 404 masquée
  (`USER_ROLE_ASSIGNMENT_NOT_FOUND`), pas 400 `ACCESS_DENIED` — preuve que c'est bien le garde-fou
  d'appartenance indépendant qui rejette, pas seulement la permission.

## 5. Critère de sortie

| Critère | Résultat |
|---|---|
| Organization/Tenant : findById/findAll/delete self-only, updatePhoto self-only (Tenant) | **Vérifié**. |
| User : findById/findByEmail/findAll/delete scopés via idEntreprise (pas organizationId) | **Vérifié**. |
| User.updatePhoto self-only (même motif que changePassword) | **Vérifié**. |
| Role/Permission : findById/findAll désormais gatés par requireRbacManage | **Vérifié**. |
| UserRoleAssignment : findById/findAllByUser gatés + garde-fou d'appartenance indépendant, ordre correct (appartenance avant hasPermission) | **Vérifié** — test dédié au contournement GLOBAL. |
| Appelants internes (login, adaptateur legacy) toujours fonctionnels sans filtrage | **Vérifié**. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 233 tests, 0 échec, 0 erreur (221 avant l'incrément, +12 tests dédiés). |

## 6. Fichiers modifiés

**Production** : `organization/application/OrganizationService.java`,
`organization/application/impl/OrganizationServiceImpl.java`,
`organization/presentation/rest/OrganizationController.java`,
`tenant/application/TenantService.java`, `tenant/application/impl/TenantServiceImpl.java`,
`tenant/presentation/rest/TenantController.java`,
`identity/application/UserService.java`, `identity/application/impl/UserServiceImpl.java`,
`identity/presentation/rest/UserController.java`,
`identity/presentation/rest/{RoleController,PermissionController}.java`,
`identity/application/UserRoleAssignmentService.java`,
`identity/application/impl/UserRoleAssignmentServiceImpl.java`,
`identity/presentation/rest/UserRoleAssignmentController.java`,
`exception/ErrorCodes.java` (+`USER_UPDATE_PHOTO_FORBIDDEN`).

**Test** : `organization/OrganizationModuleIntegrationTest.java`,
`organization/OrganizationControllersHttpIntegrationTest.java` (+ correctif de fixture, voir §3),
`services/EntrepriseServiceOrganizationMirrorIntegrationTest.java` (adaptation mécanique),
`tenant/TenantServiceImplTest.java` (nouveau), `identity/UserServiceImplTest.java` (nouveau),
`media/PhotoAttachmentCharacterizationTest.java`,
`identity/RbacAdminEndpointsHttpIntegrationTest.java`.

**Docs** : `docs/migration-notes.md`.

## 7. Prochaine étape

5b-2c terminé et vérifié vert — 5b-2 (lecture scopée par organisation, RBAC) est désormais
complet dans son intégralité (5b-2a, 5b-2b, 5b-2c). Gaps délibérément reportés, tous documentés
et retrouvables : RBAC-on-create pour Sale/CustomerOrder/PurchaseOrder/StockTransfer/Organization/
Tenant/Article/Category/Customer/Supplier (Phase 5b-1 §8, Phase 0), `InventoryController.receive/
issue/correct` (5b-2b §4), `SiteController.save()` sans vérification d'appartenance de City
(5b-2b §4), `OrganizationController.save()` sans aucune vérification (ce rapport §1). **N'enchaîne
sur aucun de ces points sans feu vert explicite.**
