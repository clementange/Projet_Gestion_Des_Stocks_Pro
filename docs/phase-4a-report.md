# Phase 4a — migrer `model.Utilisateur` vers `identity.User` — Rapport

Premier incrément de la Phase 4 (extinction du legacy plat) et le code le plus sensible touché
depuis le début de cette migration : l'identité réelle de l'application (login, mot de passe,
email) et son rebranchement complet (`ApplicationUserDetailsService`, `AuthenticationController`
indirectement, `EntrepriseServiceImpl`). Précédé d'un plan complet validé explicitement avant tout
code (voir échanges précédents), conformément à la règle "investigation-first" demandée.

## 0. Deux découvertes de sécurité hors périmètre, signalées séparément et non corrigées

Trouvées en lisant `SecurityConfiguration`/`EntrepriseServiceImpl` pour comprendre la chaîne
d'authentification avant d'écrire le plan :

1. **`EntrepriseServiceImpl.generateRandomPassword()` retourne une constante en dur**
   (`"som3R@nd0mP@$$word"`) — exactement le pattern interdit par CLAUDE.md ("jamais de valeur
   constante retournée par une méthode nommée random/generate"). **12 fichiers de test**
   dépendent de cette valeur littérale pour obtenir un token admin. Non corrigé dans 4a : le
   corriger proprement suppose de redessiner le flux de bootstrap admin (récupérer/communiquer le
   mot de passe généré) et casserait les 12 fichiers de test — hors périmètre d'un incrément de
   migration.
2. **`SecurityConfiguration.corsFilter()`** combine `allowCredentials(true)` avec
   `allowedOriginPatterns("*")` — combinaison interdite par CLAUDE.md, déjà commentée dans le code
   (`// Don't do this in production`). Non touché, hors du fichier-scope de 4a.

Les deux sont accepté par l'utilisateur comme non traités dans 4a. Voir `docs/migration-notes.md`
pour leur suivi.

## 1. Étape 0 — caractérisation AVANT tout changement

Aucun test dédié n'existait sur le flux d'authentification legacy (`UtilisateurController`,
`UtilisateurServiceImpl`, `/auth/authenticate`), contrairement aux 7 autres adaptateurs legacy
caractérisés en Phase 0. Écriture de
`legacy/UtilisateurAuthenticationCharacterizationTest.java` (12 tests HTTP réels, `MockMvc` + vrai
filtre de sécurité) **avant tout changement de code**, couvrant : login réussi + claims JWT exacts
(`sub`, `idEntreprise`, `idUtilisateur`), login refusé (mauvais mot de passe → 400
`BAD_CREDENTIALS`), IDOR de `changerMotDePasse` reproduit tel quel, absence de vérification de
permission sur `create` reproduite telle quelle, `create` rejette email dupliqué/champs
manquants, `findById`/`findByEmail` inconnus → `UTILISATEUR_NOT_FOUND`, `findAll`, `delete`.

**Écart trouvé pendant l'écriture, avant même de toucher au code** : mon hypothèse initiale
("login avec email inconnu → 400 `BAD_CREDENTIALS`", par symétrie avec mauvais mot de passe)
était fausse. Le comportement réel actuel : `UtilisateurServiceImpl.findByEmail()` lève une
`EntityNotFoundException` (notre exception métier) **à l'intérieur** de
`ApplicationUserDetailsService.loadUserByUsername()` ; `DaoAuthenticationProvider.retrieveUser()`
n'attrape que `UsernameNotFoundException` à cet endroit précis — notre exception remonte donc
enveloppée en `InternalAuthenticationServiceException`, capturée uniquement par le handler
générique (`RestExceptionHandler.handleUnexpectedException`) → **500, `code: null`**, pas 400
`BAD_CREDENTIALS`. Asymétrie réelle et pré-existante entre "mauvais mot de passe" (400, géré
explicitement) et "email inconnu" (500, géré par accident) — test corrigé pour refléter la
réalité observée, **pas corrigé dans le code** (même catégorie que les bugs déjà actés : reproduit
identique après migration, voir §4).

- **Avant** tout changement de production : **12/12 verts.**
- **Après** la migration complète : **12/12 verts, identiques**, 0 assertion modifiée (au-delà de
  la correction faite avant le premier run, pour refléter la réalité et non mon hypothèse).

## 2. Agrégat `identity.domain.model.User`

- **Nom** : `User`, pas `Utilisateur` — cohérence avec le vocabulaire déjà utilisé dans le module
  (`UserRoleAssignment.userId`, vocabulaire anglais imposé par CLAUDE.md aux modules neufs).
- **Structure** : alignée sur le reste du module `identity`
  (`application/`, `application/dto/`, `application/impl/`, `application/validator/`,
  `domain/model/`, `infrastructure/persistence/`, `presentation/rest/` — pas `adapter/in|out`,
  écart déjà accepté en Phase 0/1, reconduit ici sans y toucher).
- **Mapping** : `orm.xml`, même technique que toute la Phase 3c. **Table réutilisée telle quelle**
  (`utilisateur`, aucune nouvelle migration Flyway) — minimise le risque sur le code le plus
  sensible touché jusqu'ici.
- **Champs portés** : `nom`, `prenom`, `email`, `dateDeNaissance`, `motDePasse` (orthographe
  corrigée — voir §3), `adresse` (`Adresse` embarquée), `photo`, `idEntreprise` (`Long`, référence
  faible comme `PurchaseOrder.supplierId` — confirmé : `utilisateur.identreprise` porte une vraie
  FK vers `entreprise(id)` en base, mais Hibernate n'exige pas que le champ soit une relation pour
  la respecter).
- **`roles` non porté** : confirmé indépendamment que `model.Roles` n'a **jamais pu être persisté**
  — aucun `RolesRepository` n'existe dans le code, pas seulement "jamais lu" pour les autorités
  (déjà confirmé que `ApplicationUserDetailsService` ne lit que `UserRoleAssignmentService`). La
  table `roles` est inerte depuis toujours. `model.Roles`/`dto.RolesDto` deviennent orphelins,
  hors périmètre de suppression ici.
- **Aucun invariant métier** : l'unicité d'email et le hachage de mot de passe sont des
  préoccupations applicatives (accès repository, bean `PasswordEncoder`), pas des règles portées
  par l'agrégat — comme `Customer`/`Supplier`. Pas de golden master pur-domaine nécessaire (voir
  Finition B).

## 3. Décisions signalées explicitement (validées par l'utilisateur avant code)

- **`motDePasse` (faute historique `moteDePasse`)** : corrigé dans le nouvel agrégat/DTO
  (`identity.application.dto.UserDto`, contrat neuf sans frontend). **Gardé tel quel** dans
  `dto.UtilisateurDto`/`ChangerMotDePasseUtilisateurDto` (legacy, contrat JSON figé).
- **DTO en `@Data @Builder` Lombok**, pas `record` — cohérence avec `PermissionDto`/`RoleDto`/
  `UserRoleAssignmentDto`, déjà tous dans ce style au sein du même module (écart CLAUDE.md déjà
  accepté). Seul `ChangePasswordRequest` (contrat neuf, endpoint neuf, aucune contrainte
  d'héritage de style) est un `record`.
- **Contrat HTTP `/users/*` exposé** (nouveau, canonique) **en parallèle** de `/utilisateurs/*`
  (legacy, inchangé) — décision validée. `/users/*` reprend la **même posture de sécurité** que
  l'existant (`authenticated()` seul, aucune vérification de permission fine) : pas une
  amélioration délibérée, un choix de ne pas durcir une surface neuve au-delà de ce que
  l'existant offrait déjà, dans le même esprit que "ne pas corriger les bugs connus pendant la
  migration".
- **`UtilisateurDto.roles`** : retiré silencieusement (jamais alimenté, aucun test n'y touchait) —
  disparaît du JSON plutôt que de rester forcé à `[]`.

## 4. Rebranchement — comportement observable préservé

- **`ApplicationUserDetailsService`** : dépend désormais de `identity.application.UserService` +
  `UserRoleAssignmentService` (déjà le cas) au lieu de `services.UtilisateurService`. Logique
  inchangée : email → `UserDto` → `ExtendedUser(email, motDePasse, idEntreprise, id, authorities)`.
  L'asymétrie 500/400 du §1 est reproduite **naturellement** (aucune traduction d'exception
  ajoutée) — pas un correctif accidentel.
- **`EntrepriseServiceImpl`** : `fromEntreprise()`/bootstrap admin rebranchés sur
  `identity.application.UserService` directement (cohérent avec son usage déjà direct de
  `RoleService`/`UserRoleAssignmentService` pour le RBAC). `generateRandomPassword()` **non
  touché** (voir §0).
- **`AuthenticationController`/`JwtUtil`** : aucun changement (ne dépendent pas de
  `UtilisateurService`/`Utilisateur`). Claims JWT vérifiés identiques par le golden master (§1).
- **`services.impl.UtilisateurServiceImpl`** (legacy) : devient un adaptateur fin re-backé sur
  `identity.application.UserService`, exact même pattern que `FournisseurServiceImpl`/
  `purchasing.Supplier` (Phase 18). Contrairement à Fournisseur/Client (dont les 404 laissent
  déjà fuiter le code du module neuf — comportement déjà caractérisé avant leur propre
  migration), **les codes d'erreur legacy sont ici explicitement traduits**
  (`USER_NOT_FOUND`→`UTILISATEUR_NOT_FOUND`, `USER_ALREADY_EXISTS`→`UTILISATEUR_ALREADY_EXISTS`,
  `USER_NOT_VALID`→`UTILISATEUR_NOT_VALID`, message et liste d'erreurs réutilisés tels quels)
  parce que cette migration se fait **maintenant** : le comportement observable avant ce commit
  doit rester identique après, pas se rapprocher d'un nouveau standard. `delete()` délègue
  directement sans traduction (même absence de garde qu'avant : `deleteById` sur ID inconnu
  produit la même exception Spring non traduite qu'auparavant). Reconstruction du champ
  `entreprise` imbriqué (forme JSON legacy inchangée) via une consultation directe
  d'`EntrepriseRepository` à partir de `idEntreprise` — nécessaire puisque `identity.User` ne
  porte plus qu'une référence faible.
- **`dto.UtilisateurDto`** : `fromEntity`/`toEntity` gardés **tels quels** (code mort, jamais plus
  appelés) plutôt que supprimés ou réécrits — même état que `dto.CommandeFournisseurDto` depuis
  la Phase 21, pour ne pas toucher `model.Utilisateur`/`UtilisateurRepository`.

## 5. État de `model.Utilisateur`/`UtilisateurRepository` après 4a

Confirmé avant modification que seuls `UtilisateurServiceImpl` et `dto.UtilisateurDto`
référençaient `UtilisateurRepository`/`model.Utilisateur`. Après le rebranchement, **les deux
deviennent orphelins** (plus référencés que par leur propre déclaration) — exactement l'état de
`model.CommandeFournisseur`/`CommandeFournisseurRepository` depuis la Phase 21 (vérifié comme
précédent directement applicable). Laissés en place, annotations JPA inchangées, **non supprimés dans cet
incrément** — cohérent avec "pas de suppression de legacy orphelin, une finition ultérieure".
Deux mappings JPA indépendants coexistent désormais sur la table `utilisateur`
(`model.Utilisateur` par annotations, `identity.User` par `orm.xml`) : sans risque, puisque plus
aucun code n'écrit via le premier.

## 6. Bugs hérités, non corrigés — consignés dans `docs/migration-notes.md`

- `changerMotDePasse` : aucune vérification que l'appelant est la cible ou un admin (IDOR) —
  reproduit et testé tel quel (§1).
- `create`/`delete`/`find*` (`/utilisateurs/*` **et** `/users/*`) : aucune vérification de
  permission au-delà de `authenticated()` — reproduit et testé tel quel (§1), et délibérément
  reconduit sur la surface neuve (§3).
- Asymétrie login mauvais-mot-de-passe (400) vs email-inconnu (500) — découverte pendant cet
  incrément (§1), reproduite à l'identique.

## 7. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master caractérisation vert avant ET après, sans modification d'assertion (hors correction pré-premier-run) | **Vérifié** — 12/12 avant, 12/12 après, identiques. |
| `ApplicationUserDetailsService`/`AuthenticationController`/`EntrepriseServiceImpl` rebranchés sans changement de comportement observable | **Vérifié** — mêmes claims JWT, même contrat `/auth/authenticate`, même bootstrap admin (mot de passe toujours en dur, non corrigé). |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** — 6/6 et 2/2, aucune nouvelle violation, aucun cycle de module introduit. |
| Tous les tests existants verts | **Vérifié** — `./mvnw clean verify` : 226 tests (214 + 12 nouveaux), 0 échec, 0 erreur. |
| Compatibilité `/utilisateurs/*` (contrat, codes d'erreur, JSON) préservée | **Vérifié** — `LegacyControllersIntegrationTest`, `SecurityFilterChainIntegrationTest`, `ArticleHistoryLegacyEndpointsTest`, `RbacAdminEndpointsHttpIntegrationTest`, `BusinessModulesHttpIntegrationTest`, `EntrepriseServiceOrganizationMirrorIntegrationTest`, `EntrepriseServiceRbacBootstrapIntegrationTest` tous verts sans modification. |

## 8. Fichiers modifiés/créés

**Production, module `identity` (nouveau)** :
- `identity/domain/model/User.java`
- `identity/application/UserService.java`, `application/impl/UserServiceImpl.java`
- `identity/application/dto/UserDto.java`, `application/dto/ChangePasswordRequest.java`
- `identity/application/validator/UserValidator.java`
- `identity/infrastructure/persistence/UserRepository.java`
- `identity/presentation/rest/UserController.java`
- `src/main/resources/META-INF/orm.xml` (complété)

**Production, legacy (rebranchés/adaptés, pas supprimés)** :
- `services/auth/ApplicationUserDetailsService.java`
- `services/impl/EntrepriseServiceImpl.java`
- `services/impl/UtilisateurServiceImpl.java`
- `dto/UtilisateurDto.java` (champ `roles` retiré)
- `exception/ErrorCodes.java` (codes `USER_*` ajoutés, bloc 30000)

**Test (nouveau)** :
- `legacy/UtilisateurAuthenticationCharacterizationTest.java` — 12 tests.

**Docs** :
- `docs/migration-notes.md` — bugs hérités + 2 découvertes de sécurité hors périmètre.

## 9. Prochaine étape

4a terminé et vérifié vert sur son périmètre. `EntrepriseServiceImpl`/`Entreprise`/
`EntrepriseController` restent legacy (4b, pas fait ici) mais ne dépendent plus de
`UtilisateurService`, seulement de `identity.application.UserService` directement. **N'enchaîne
pas sur 4b sans feu vert explicite.**
