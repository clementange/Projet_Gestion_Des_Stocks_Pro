# Phase 5a — backlog sécurité, correctifs isolés — Rapport

Premier incrément du backlog sécurité (voir `docs/migration-notes.md`, accumulé depuis Phase 0).
Cinq correctifs indépendants, à faible risque, ne touchant pas `interceptor/Interceptor.java` (la
fuite de lecture cross-tenant, bien plus large que documenté à l'origine, est traitée séparément
en Phase 5b).

## 0. Vérification indépendante des prémisses, avant implémentation

Confirmé par l'utilisateur puis re-vérifié en code avant d'écrire quoi que ce soit :
`AdresseValidator` revalidait `adresse1` au lieu de `codePostale` (ligne 29 avant correctif) ;
aucun des 5 repositories (`SaleRepository`, `CustomerOrderRepository`, `PurchaseOrderRepository`,
`CustomerRepository`, `SupplierRepository`) n'avait de méthode `findByCode`/`findByMail` — sauf
découverte en cours de route : **`findSaleByCode`/`findCustomerOrderByCode`/`findPurchaseOrderByCode`
existaient déjà** (seul `findByMail` manquait vraiment, sur `CustomerRepository`/`SupplierRepository`).

## 1. CORS — `SecurityConfiguration.corsFilter()`

`allowedOriginPatterns(List.of("*"))` + `allowCredentials(true)` remplacé par une liste
d'origines explicite, configurable via `${CORS_ALLOWED_ORIGINS:http://localhost:4200}`
(`application.yml` + `.env.example`). `corsFilter()` devient un bean paramétré par `@Value`,
injecté dans `securityFilterChain` plutôt qu'appelé directement (Spring ne peut pas résoudre un
appel de méthode Java portant un paramètre `@Value` sans passer par l'injection de bean).

**Test** : `config/CorsConfigurationTest.java` (2 tests) — une origine autorisée reçoit
`Access-Control-Allow-Origin`, une origine arbitraire ne le reçoit pas. Vérifié sur une route
protégée (`CorsFilter` s'exécute avant tout filtre d'authentification, donc observable même sans
jeton).

## 2. `AdresseValidator` — bug `codePostale` jamais contrôlé

Dernier `if` de `validate()` appelait `getAdresse1()` une seconde fois au lieu de
`getCodePostale()` (copier-coller). `codePostale` n'était donc jamais contrôlé nulle part dans
l'application (Client/Fournisseur avant Phase 4d, aujourd'hui `TenantRegistrationValidator`,
`UserValidator`, `UtilisateurValidator`, qui appellent tous `AdresseValidator`). Correctif d'une
ligne. Aucun test existant ne dépendait du bug (le seul qui le caractérisait,
`ClientCharacterizationTest.createWithoutCodePostaleStillSucceedsBecauseOfAdresseValidatorBug`, a
été supprimé avec le reste de la chaîne Client en Phase 4d).

**Test** : `validator/AdresseValidatorTest.java` (4 tests, JUnit pur, pas de contexte Spring) —
`codePostale` manquant/vide désormais rejeté, adresse complète toujours acceptée, `adresse1`
manquant toujours rejeté indépendamment (pas de régression sur les 3 autres champs).

## 3. IDOR sur le changement de mot de passe — self-only

`/utilisateurs/update/password` (`id` dans le corps) et `/users/{id}/password` (`id` en chemin)
ne vérifiaient l'identité de l'appelant nulle part — n'importe quel utilisateur authentifié
pouvait changer le mot de passe de n'importe quel autre. Corrigé en **self-only** (l'appelant doit
être la cible), décision explicitement validée : pas de nouvelle permission RBAC pour un override
admin dans cet incrément (ça resterait à faire dans l'item RBAC déjà exclu de 5a). Nouveau code
`USER_CHANGE_PASSWORD_FORBIDDEN` (400, via `InvalidOperationException`, même famille que
`ROLE_ACCESS_DENIED`/`*_ACCESS_DENIED`).

- `identity.presentation.rest.UserController.changePassword` : ajout
  `@AuthenticationPrincipal ExtendedUser principal`, garde `requireSelf`.
- `controller/api/UtilisateurApi.java`/`controller/UtilisateurController.java` : même garde,
  directement dans l'adaptateur legacy (correctif de sécurité sur une fonctionnalité existante,
  pas une fonctionnalité neuve — reste dans le legacy plat sans déplacement, cohérent avec
  CLAUDE.md).

**Tests** — `UtilisateurAuthenticationCharacterizationTest.java` :
- `changerMotDePasseHasNoOwnershipCheckIdorReproducedAsIs` **renommé et inversé** en
  `changerMotDePasseRejectsWhenCallerIsNotTargetUser` (attend désormais 400
  `USER_CHANGE_PASSWORD_FORBIDDEN` + vérifie que l'ancien mot de passe de la victime fonctionne
  toujours). Conformément à l'instruction : ce n'est pas une exception à "ne jamais modifier un
  test sans comprendre pourquoi il échouait", c'est le cas où le comportement qu'il verrouillait a
  été volontairement changé.
- `changerMotDePasseSucceedsWhenCallerIsTargetUser` (nouveau) : le chemin légitime continue de
  fonctionner.
- `usersRoutePasswordChangeRejectsWhenCallerIsNotTargetUser` (nouveau) : même IDOR vérifié sur la
  route module-neuf `/users/{id}/password`, qui n'avait aucun test symétrique avant cet incrément.

## 4. Asymétrie 500/400 sur email inconnu à l'authentification

`ApplicationUserDetailsService.loadUserByUsername` laissait remonter
`EntityNotFoundException` telle quelle (email inconnu → 500 brut, `code: null`), alors qu'un
mauvais mot de passe donnait 400 `BAD_CREDENTIALS`. Au-delà de l'incohérence de statut, cette
asymétrie était un oracle d'énumération de comptes (le code HTTP seul révélait si l'email
existait). Corrigé en traduisant l'exception en `UsernameNotFoundException`, que
`DaoAuthenticationProvider` traduit déjà (comportement standard Spring Security,
`hideUserNotFoundExceptions=true` par défaut) en `BadCredentialsException` → même 400
`BAD_CREDENTIALS` que pour un mauvais mot de passe.

**Test** : `loginFailsForUnknownEmailWith500NotBadCredentials` **renommé et inversé** en
`loginFailsForUnknownEmailWith400BadCredentialsSameAsWrongPassword` (attend désormais 400
`BAD_CREDENTIALS`, même code que le test symétrique sur mauvais mot de passe).

## 5. Unicité applicative sur les codes de commande/vente et les mails Customer/Supplier

Contrainte `UNIQUE` en base seule (`DataIntegrityViolationException` → 500 brut, sans champ
`code`) sur `Sale.code`/`CustomerOrder.code`/`PurchaseOrder.code`, et aucune contrainte du tout
(applicative ou base) sur `Customer.mail`/`Supplier.mail`. Pré-check applicatif ajouté dans
chaque `create()`/`save()`, avant l'écriture, avec un nouveau code d'erreur `*_ALREADY_EXISTS` par
entité (`SALE_ALREADY_EXISTS`, `CUSTOMER_ORDER_ALREADY_EXISTS`, `PURCHASE_ORDER_ALREADY_EXISTS`,
`CUSTOMER_ALREADY_EXISTS`, `SUPPLIER_ALREADY_EXISTS`).

`CustomerRepository`/`SupplierRepository` (auparavant de simples `JpaRepository<T, Long>` vides)
gagnent un `findByMail`. `Customer.save()`/`Supplier.save()` étant des upserts, le pré-check
**exclut l'id de l'entité elle-même** — sans ça, mettre à jour un client sans changer son mail
serait rejeté à tort comme doublon de lui-même (même classe d'erreur déjà trouvée et corrigée sur
`User`/`Utilisateur` en Phase 4a, pas répétée ici).

Écart assumé : le pré-check + l'écriture ne sont pas atomiques (une vraie course reste
théoriquement possible entre le `findByCode`/`findByMail` et le `save()`) — la contrainte `UNIQUE`
en base reste le filet de sécurité final dans ce cas rarissime ; l'objectif ici est de transformer
le cas courant (500 brut) en réponse propre, pas de remplacer la contrainte base.

**Tests** :
- `CustomerServiceImplTest`/`SupplierServiceImplTest` (+2 chacun) : mail dupliqué rejeté à la
  création, mise à jour du même enregistrement sans conflit sur son propre mail.
- `SaleServiceIntegrationTest`/`CustomerOrderServiceIntegrationTest`/`PurchaseOrderServiceIntegrationTest`
  (+1 chacun) : code dupliqué rejeté avec le nouveau code d'erreur propre au lieu d'un 500 brut.

## 6. Critère de sortie

| Correctif | Résultat |
|---|---|
| CORS : origine explicite, pas de wildcard+credentials | **Vérifié** — 2 tests HTTP. |
| `AdresseValidator` : `codePostale` contrôlé | **Vérifié** — 4 tests unitaires. |
| IDOR mot de passe : self-only sur les deux routes | **Vérifié** — 3 tests HTTP (legacy + module neuf). |
| Asymétrie auth : 400 uniforme, plus d'oracle d'énumération | **Vérifié** — 1 test HTTP. |
| Unicité code Sale/CustomerOrder/PurchaseOrder, mail Customer/Supplier | **Vérifié** — 7 tests (2 unitaires + 5 integration). |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 205 tests, 0 échec, 0 erreur (190 avant l'incrément, +15 nouveaux/modifiés net). |

## 7. Fichiers modifiés

**Production** : `config/SecurityConfiguration.java`, `application.yml`, `.env.example`,
`validator/AdresseValidator.java`, `identity/presentation/rest/UserController.java`,
`controller/api/UtilisateurApi.java`, `controller/UtilisateurController.java`,
`services/auth/ApplicationUserDetailsService.java`, `exception/ErrorCodes.java`,
`sales/application/impl/SaleServiceImpl.java`,
`sales/application/impl/CustomerOrderServiceImpl.java`,
`purchasing/application/impl/PurchaseOrderServiceImpl.java`,
`sales/application/impl/CustomerServiceImpl.java`,
`purchasing/application/impl/SupplierServiceImpl.java`,
`sales/infrastructure/persistence/CustomerRepository.java`,
`purchasing/infrastructure/persistence/SupplierRepository.java`.

**Test** : `config/CorsConfigurationTest.java` (nouveau), `validator/AdresseValidatorTest.java`
(nouveau), `legacy/UtilisateurAuthenticationCharacterizationTest.java` (2 tests renommés/inversés,
2 nouveaux), `sales/CustomerServiceImplTest.java`, `purchasing/SupplierServiceImplTest.java`,
`sales/SaleServiceIntegrationTest.java`, `sales/CustomerOrderServiceIntegrationTest.java`,
`purchasing/PurchaseOrderServiceIntegrationTest.java` (2 nouveaux tests chacun/1 selon le fichier).

**Docs** : `docs/migration-notes.md`.

## 8. Prochaine étape

5a terminé et vérifié vert. Reste ouvert dans le backlog sécurité : le leak cross-tenant
(Phase 5b, dédiée, touche `interceptor/Interceptor.java`), les gaps RBAC sur
`Sale`/`CustomerOrder`/`PurchaseOrder.create/validate` et `/users/*`/`/utilisateurs/*`
create/delete/find. **N'enchaîne pas sur un autre incrément sans feu vert explicite.**
