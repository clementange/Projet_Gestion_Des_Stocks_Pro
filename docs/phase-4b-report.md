# Phase 4b — migrer `model.Entreprise` vers `tenant`, corriger le mot de passe admin en dur — Rapport

Deuxième incrément de la Phase 4, sur le code le plus sensible touché avec 4a : l'inscription
d'une nouvelle entreprise (Organization miroir, Site par défaut, premier utilisateur admin,
amorçage RBAC), avec correction délibérée du mot de passe admin généré en dur
(`EntrepriseServiceImpl.generateRandomPassword()`). Précédé d'un plan complet validé
explicitement (décisions d'architecte imposées : module `tenant`, mot de passe fourni par
l'appelant, contrat `/entreprises/create` autorisé à changer) et de 4 points laissés ouverts,
tranchés dans le plan puis validés : endpoint `/tenants/register`, rétention complète de
`EntrepriseService`/`Controller`/`Dto` (rejetée au profit d'une suppression), champs admin
explicites, politique de mot de passe 8 caractères + confirmation.

## 0. Découverte structurante trouvée en investiguant, qui a reformé la conception

Avant d'écrire le plan, recherche de tous les consommateurs de `model.Entreprise`/
`EntrepriseRepository` : **6 `*ServiceImpl` legacy** (`ClientServiceImpl`,
`FournisseurServiceImpl`, `VentesServiceImpl`, `MvtStkServiceImpl`,
`CommandeFournisseurServiceImpl`, `CommandeClientServiceImpl`) en dépendent directement pour
traduire `idEntreprise ↔ organizationId` (méthodes `resolveOrganizationId`/`resolveIdEntreprise`
copiées-collées à l'identique dans les 6 fichiers). Décision qui en découle : **`model.Entreprise`/
`EntrepriseRepository` restent intacts, toujours `@Entity`**, exactement le traitement de
`model.Utilisateur`/`UtilisateurRepository` en Phase 4a — ces 6 fichiers n'ont **pas été
modifiés**, ils continuent de lire la même table physique `entreprise`, désormais alimentée par
`tenant.TenantRegistrationService` au lieu de l'ancien `EntrepriseServiceImpl`.

Deux consommateurs réels supplémentaires trouvés (non mentionnés dans le contexte initial, à
l'image de `SaveUtilisateurPhoto` en 4a) : `services/strategy/SaveEntreprisePhoto.java` (upload
photo Flickr) et `dto/UtilisateurDto.entreprise` (champ imbriqué du contrat JSON
`/utilisateurs/*`). Tous deux rebranchés sur `tenant.application.TenantService`/`TenantDto` (§4).

**Bug latent trouvé en lisant `EntrepriseServiceImpl.save()`** : aucune distinction
création/mise à jour — un appel avec un `id` déjà existant (exactement ce que fait
`SaveEntreprisePhoto.savePhoto()` pour persister une URL de photo) redéclenchait **toute**
l'orchestration (nouvelle Organization miroir, nouveau Site, nouvel admin, nouvelle attribution
RBAC) à chaque changement de photo. Non corrigé comme un correctif ciblé — éliminé structurellement
par la séparation `TenantService.save()` (upsert plat)/`TenantRegistrationService.register()`
(orchestration), une conséquence du refactor demandé, pas un correctif additionnel.

## 1. Module `tenant`

```
tenant/domain/model/Tenant.java
tenant/application/TenantService.java (+impl)             — CRUD simple, save() sans orchestration
tenant/application/TenantRegistrationService.java (+impl) — register(), l'orchestration complete
tenant/application/dto/TenantDto.java
tenant/application/dto/TenantRegistrationRequest.java     — record
tenant/application/validator/TenantValidator.java
tenant/application/validator/TenantRegistrationValidator.java
tenant/infrastructure/persistence/TenantRepository.java
tenant/presentation/rest/TenantController.java
```

Mapping `orm.xml`, table `entreprise` réutilisée telle quelle, aucune nouvelle migration Flyway.
`organizationId` répète le cas `numTel`/`num_tel` (Phase 3c/purchasing, Phase 3c/sales) : nom de
colonne littéral (`organizationId`) différent du nom physique réel (`organization_id`, ajoutée
par `ALTER` dans `V5`), résolu par `SpringPhysicalNamingStrategy`, confirmé par
`ddl-auto=validate`.

## 2. Décisions tranchées dans le plan, exécutées telles quelles

- **`EntrepriseService`/`EntrepriseServiceImpl`/`EntrepriseController`/`EntrepriseApi`/
  `EntrepriseDto`/`EntrepriseValidator` : supprimés**, pas gardés en adaptateur fin. Un
  `/entreprises/create` sans l'orchestration n'aurait plus aucune utilité (ne créerait ni
  Organization ni admin), et ses 2 vrais consommateurs (`SaveEntreprisePhoto`,
  `UtilisateurServiceImpl`/`UtilisateurDto`) sont rebranchés directement sur `tenant` (§0, §4).
- **Endpoint `POST /tenants/register`**, public (`SecurityConfiguration.permitAll()` mis à jour :
  `/entreprises/create` retiré, `/tenants/register` ajouté — chemin exact, pas de wildcard `**`,
  même style que le reste de la liste).
- **Champs admin explicites** dans `TenantRegistrationRequest` (`adminNom`, `adminPrenom`,
  `adminEmail`, `adminDateDeNaissance`) plutôt que la reprise trouvée dans l'ancien code
  (`prenom(dto.getCodeFiscal())` — le code fiscal de l'entreprise utilisé comme prénom de
  l'admin, un bouche-trou improvisé, pas un choix voulu).
- **Politique de mot de passe** : 8 caractères minimum + correspondance des deux champs, pas
  d'exigence de complexité — nouvelle règle (n'existait pas avant, le mot de passe n'étant jamais
  fourni par l'appelant), implémentée dans `TenantRegistrationValidator`.

## 3. Écart trouvé et corrigé pendant l'implémentation (avant tout commit)

Premier jet de `TenantRegistrationServiceImpl.register()` : l'admin `UserDto` construit sans
`adresse` (`UserValidator` l'exige, même règle que l'ancien `UtilisateurValidator`). Détecté
immédiatement par `EntrepriseServiceOrganizationMirrorIntegrationTest`/
`EntrepriseServiceRbacBootstrapIntegrationTest` (réécrits, voir §5) échouant avec
`USER_NOT_VALID`/"L'utilisateur n'est pas valide" avant même le premier commit — corrigé en
réutilisant l'adresse de l'entreprise pour l'admin, même comportement que
`EntrepriseServiceImpl.fromEntreprise()` avant cet incrément (qui n'avait pas non plus de champ
adresse admin séparé).

## 4. Rebranchement des 2 vrais consommateurs

- **`SaveEntreprisePhoto`** : dépend désormais de `tenant.application.TenantService`/`TenantDto`
  au lieu de `EntrepriseService`/`EntrepriseDto`. `PhotoController`/`StrategyPhotoContext`
  inchangés (dispatch générique par nom de bean, aucun couplage au type).
- **`dto.UtilisateurDto.entreprise`** : type changé de `EntrepriseDto` (supprimé) vers
  `tenant.application.dto.TenantDto` — mêmes noms de champs, même forme JSON. Les méthodes
  `fromEntity`/`toEntity` (déjà mortes depuis la Phase 4a) ont été **retirées** : elles ne
  pouvaient plus être honnêtement écrites, `model.Utilisateur.getEntreprise()` retournant
  `model.Entreprise`, un type distinct de `tenant.domain.model.Tenant` malgré la même table
  physique.
- **`services.impl.UtilisateurServiceImpl`** : dépend désormais de `tenant.application.TenantService`
  au lieu d'`EntrepriseRepository` direct — reconstruit le champ `entreprise` imbriqué via
  `tenantService.findById(idEntreprise)`, avec le même repli gracieux vers `null` en cas
  d'absence (`try/catch EntityNotFoundException`) que le `.orElse(null)` original.
- **`dto.RolesDto.toEntity()`** : appelait `UtilisateurDto.toEntity()`, désormais retiré — la
  ligne `roles.setUtilisateur(...)` a été supprimée. Sans conséquence : `RolesDto`/`model.Roles`
  sont déjà, depuis la Phase 4a, confirmés totalement orphelins (aucun `RolesRepository` n'a
  jamais existé, cette méthode n'est jamais appelée).

## 5. Étape 0 — caractérisation

**Rewrites, pas adaptations** (le point d'entrée `EntrepriseServiceImpl.save()` a disparu) :
`EntrepriseServiceOrganizationMirrorIntegrationTest` et `EntrepriseServiceRbacBootstrapIntegrationTest`
réécrits pour appeler `TenantRegistrationService.register(TenantRegistrationRequest...)`, mêmes
assertions finales qu'avant (Organization miroir + Site par défaut ; rôle ADMINISTRATEUR/GLOBAL
avec `SALE_CREATE`). `EntrepriseServiceRbacBootstrapIntegrationTest` recherche désormais
l'utilisateur admin via `identity.application.UserService.findByEmail` (le module qui le possède
réellement) plutôt que via l'`UtilisateurRepository` legacy orphelin.

**Nouveaux tests** (`tenant/TenantRegistrationCharacterizationTest`, 5 méthodes) — ce qui
n'existait pas avant : inscription réussie **et connexion immédiate avec le mot de passe fourni**
(preuve directe qu'aucun mot de passe serveur n'existe plus), rejet mot de passe < 8 caractères,
rejet mots de passe non concordants, rejet email admin dupliqué (`USER_ALREADY_EXISTS`, propagé
tel quel — pas de traduction de code ici puisque `/tenants/register` est un contrat neuf, pas un
adaptateur legacy à préserver byte-for-byte), rejet champs requis manquants.

**14 fichiers de test adaptés** (13 dépendant de `"som3R@nd0mP@$$word"` + les 2 réécrits ci-dessus,
soit un de plus que les "12" initialement recensés : `UtilisateurAuthenticationCharacterizationTest`,
créé pendant la Phase 4a, en dépendait aussi) — conséquence directe et nécessaire d'un contrat
volontairement changé, pas une exception à la règle "ne jamais modifier un test sans comprendre
pourquoi il échouait" : `RbacAdminEndpointsHttpIntegrationTest`, `SecurityFilterChainIntegrationTest`
(2 occurrences), `BusinessModulesHttpIntegrationTest`, `CommandeClientCharacterizationTest`,
`LegacyControllersIntegrationTest` (2 occurrences), `OrganizationControllersHttpIntegrationTest`,
`FournisseurCharacterizationTest`, `ClientCharacterizationTest`, `VentesCharacterizationTest`,
`CommandeFournisseurCharacterizationTest`, `MvtStkCharacterizationTest`,
`ArticleHistoryLegacyEndpointsTest`, `UtilisateurAuthenticationCharacterizationTest`. Chaque
`adminToken()`/`adminTenant()`/`tokenFor()` poste désormais vers `/tenants/register` avec un mot
de passe fourni par le test lui-même (`"Test-Passw0rd!"`, littéral partagé entre tests, pas de
constante centralisée créée — cohérent avec l'absence de helper partagé préexistant pour ce
usage).

## 6. Écart Spring Modulith trouvé et corrigé (gap pré-existant, pas introduit ici)

`ModularityTests` a détecté que `tenant` dépend de types non exposés dans
`identity.application.dto` (`UserDto`, `UserRoleAssignmentDto`). Cause : ce sous-package n'avait
**jamais eu de `package-info.java`/`@NamedInterface`**, contrairement à
`organization.application.dto` (qui en a un, documentant explicitement que
`SiteDto`/`OrganizationDto` sont utilisés par d'autres modules). Le gap existait déjà avant cet
incrément mais n'avait jamais été exercé : seul du code legacy plat (hors périmètre
ArchUnit/Modulith) appelait ces types directement jusqu'ici ; `tenant` est le premier module
réellement enregistré à le faire. Corrigé en ajoutant
`identity/application/dto/package-info.java` avec `@NamedInterface("dto")`, même modèle exact que
`organization.application.dto`.

## 7. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Golden master réécrit vert, `TenantRegistrationCharacterizationTest` vert | **Vérifié** — 1/1, 1/1, 5/5. |
| `generateRandomPassword()` supprimé, mot de passe admin fourni par l'appelant | **Vérifié** — `EntrepriseServiceImpl` supprimé entièrement, `TenantRegistrationServiceImpl` n'a aucune génération serveur. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** — 6/6 et 2/2, après correction du gap `@NamedInterface` (§6). |
| Tous les tests existants verts | **Vérifié** — `./mvnw clean verify` : 231 tests (226 + 5 nouveaux), 0 échec, 0 erreur. |
| Les 6 adaptateurs legacy dépendant d'`EntrepriseRepository` non modifiés | **Vérifié** — `ClientServiceImpl`/`FournisseurServiceImpl`/`VentesServiceImpl`/`MvtStkServiceImpl`/`CommandeFournisseurServiceImpl`/`CommandeClientServiceImpl` : zéro diff. |
| `model.Entreprise`/`EntrepriseRepository`/`model.Utilisateur` inchangés | **Vérifié** — toujours `@Entity`, aucune annotation retirée. |

## 8. Fichiers modifiés/créés

**Production, module `tenant` (nouveau)** : `tenant/domain/model/Tenant.java`,
`tenant/application/{TenantService,TenantRegistrationService}.java` (+`impl/`),
`tenant/application/dto/{TenantDto,TenantRegistrationRequest}.java`,
`tenant/application/validator/{TenantValidator,TenantRegistrationValidator}.java`,
`tenant/infrastructure/persistence/TenantRepository.java`,
`tenant/presentation/rest/TenantController.java`.

**Production, legacy (supprimés)** : `controller/EntrepriseController.java`,
`controller/api/EntrepriseApi.java`, `services/EntrepriseService.java`,
`services/impl/EntrepriseServiceImpl.java`, `dto/EntrepriseDto.java`,
`validator/EntrepriseValidator.java`.

**Production, legacy (rebranchés/adaptés)** : `services/strategy/SaveEntreprisePhoto.java`,
`services/impl/UtilisateurServiceImpl.java`, `dto/UtilisateurDto.java`, `dto/RolesDto.java`,
`config/SecurityConfiguration.java`, `utils/Constants.java`, `exception/ErrorCodes.java`
(codes `TENANT_*` ajoutés, bloc 31000), `src/main/resources/META-INF/orm.xml`.

**Correction Modulith** : `identity/application/dto/package-info.java` (nouveau).

**Test** : `tenant/TenantRegistrationCharacterizationTest.java` (nouveau, 5 tests) + 14 fichiers
existants adaptés (§5) + 2 fichiers réécrits (§5).

**Docs** : `docs/migration-notes.md`.

## 9. Prochaine étape

4b terminé et vérifié vert sur son périmètre. Les 6 adaptateurs legacy
(Client/Fournisseur/Ventes/MvtStk/CommandeFournisseur/CommandeClient) restent inchangés,
dépendant toujours de `model.Entreprise`/`EntrepriseRepository` orphelins pour la traduction
`idEntreprise ↔ organizationId` — migration future possible mais non nécessaire ici.
`SaveEntreprisePhoto`/upload photo reste hors périmètre (déjà noté hors périmètre en Phase 4a).
**N'enchaîne pas sur un autre incrément sans feu vert explicite.**
