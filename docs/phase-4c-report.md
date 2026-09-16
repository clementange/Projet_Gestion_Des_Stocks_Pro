# Phase 4c — rehoming de l'upload de photo, module `media` (MinIO) — Rapport

Troisième incrément de la Phase 4. Contrairement à 4a/4b, pas de code d'authentification, mais un
mécanisme transversal dupliqué 5 fois, avec un dispatch en dur et deux découvertes de sécurité
supplémentaires trouvées en investiguant.

## 0. Découvertes faites en investiguant, avant tout code

**Clés Flickr en clair, troisième découverte de sécurité de la Phase 4** : `application.yml`
contenait les 4 clés Flickr en dur (`apiKey`, `apiSecret`, `appKey`, `appSecret`), sans
`${VAR_ENV:...}`, absentes de `.env.example` — violation directe du zéro-tolérance CLAUDE.md.
Résolu de fait par la suppression complète de l'intégration Flickr (pas un correctif à part).
Ces clés restent visibles dans l'historique git, hors de portée d'un correctif sans réécriture
d'historique (non fait, non demandé).

**`config/FlickrConfiguration.java`** : trouvé en compilant après suppression de la dépendance
Maven — classe jamais chargée (`@Configuration` commenté sur la classe elle-même, ligne 22),
entièrement morte. Supprimée avec le reste.

**Bug pré-existant confirmé et corrigé structurellement** : `identity.UserServiceImpl.save()`
(hérité tel quel de l'ancien `UtilisateurServiceImpl`, jamais corrigé avant) ré-encode le mot de
passe à *chaque* appel et rejette systématiquement en doublon (la vérification d'email ne
s'exclut jamais elle-même). L'ancien `SaveUtilisateurPhoto` appelait ce `save()` pour persister
une simple URL de photo — chaque upload de photo utilisateur corrompait donc silencieusement le
mot de passe et échouait très probablement avec `USER_ALREADY_EXISTS`. Confirmé en testant :
`identity.UserServiceImpl.updatePhoto()` (nouvelle méthode ciblée, même motif que
`changePassword()` déjà existant) élimine le bug comme conséquence de ne plus jamais réutiliser
`save()` pour une mise à jour — voir §4, test dédié en §5.

**Registre Docker Hub `minio/minio` inaccessible** : trouvé en exécutant les tests — MinIO Inc. a
restreint l'accès anonyme à ses images Docker Hub (nécessite une connexion depuis un changement de
licence en 2024). `quay.io/minio/minio` est le miroir public gratuit officiel, utilisé à la place
(avec `asCompatibleSubstituteFor("minio/minio")` pour satisfaire la verification de compatibilite
du module Testcontainers MinIO).

## 1. Conception retenue

**Découplage upload / attachement**, cohérent avec "capacité partagée, pas dupliquée par
module" (décision validée) :
- `media.application.MediaStorageService` (+ impl MinIO) : upload seul, aucune connaissance
  d'entité métier.
- `media.presentation.rest.MediaController` : `POST /media/upload` (multipart) → `{url}`.
- Chaque module attache lui-même l'URL à son entité via `POST /{ressource}/{id}/photo`, en
  appelant une méthode **dédiée** `updatePhoto(id, url)` — jamais le `save()` de création détourné
  (source du bug §0).

**Backend MinIO** (remplace Flickr entièrement, décision validée) : `io.minio:minio` (SDK
officiel) remplace `flickr4java` dans `pom.xml`. Bucket créé et rendu public-read au démarrage
(`@PostConstruct`) — équivalent des URLs Flickr publiques déjà consommées telles quelles
auparavant, aucun mécanisme d'URL signée/expirante n'existait avant, pas introduit ici. Config via
`${MINIO_ENDPOINT:...}`/`${MINIO_ACCESS_KEY:...}`/`${MINIO_SECRET_KEY:...}`/`${MINIO_BUCKET:...}`,
documentée dans `.env.example`, défauts = identifiants MinIO standard pour dev local uniquement
(même logique que `JWT_SECRET`).

**`Strategy`/`StrategyPhotoContext`/`PhotoController`/`PhotoApi`/les 5 `Save*Photo` supprimés
entièrement** — plus de dispatch par `switch` sur bean name, plus de retour `Object` non typé.

## 2. Où vit le rattachement par entité, et pourquoi

| Entité | Service | Contrôleur | Contrainte |
|---|---|---|---|
| Article | `catalog.ArticleService.updatePhoto` | `catalog.presentation.rest.ArticleController` (existant) | aucune, module neuf avec son propre contrôleur |
| Client (legacy) | `sales.CustomerService.updatePhoto` | **`sales.presentation.rest.legacy.ClientPhotoLegacyController`** (nouveau) | CLAUDE.md interdit toute nouvelle fonctionnalité dans le legacy plat (`services/`, `controller/`) ; `Client`/`Fournisseur` n'ont pas de contrôleur module-neuf propre (`Customer`/`Supplier` ne sont exposés que via les façades legacy) — même motif que `SalesArticleHistoryLegacyController` (Phase 3a) |
| Fournisseur (legacy) | `purchasing.SupplierService.updatePhoto` | **`purchasing.presentation.rest.legacy.FournisseurPhotoLegacyController`** (nouveau) | idem |
| Tenant | `tenant.TenantService.updatePhoto` | `tenant.presentation.rest.TenantController` (existant) | aucune |
| User | `identity.UserService.updatePhoto` | `identity.presentation.rest.UserController` (existant) | aucune ; corrige le bug §0 |

`ArticleService`/`TenantService`/`CustomerService`/`SupplierService.updatePhoto()` restent de
simples `findById → setPhoto → save()` : vérifié qu'aucun n'a le défaut de `save()` trouvé sur
`User` (pas de vérification de doublon ré-exécutée, pas de champ ré-encodé à chaque sauvegarde).

Corps de requête partagé `media.application.dto.PhotoUrlRequest` (record `{url}`), exposé via
`@NamedInterface("dto")` (`media.application.dto`) — évite de dupliquer ce record dans les 5
modules consommateurs.

**Écart assumé** : `ClientPhotoLegacyController`/`FournisseurPhotoLegacyController` retournent
respectivement `CustomerDto`/`SupplierDto` (module neuf), pas `ClientDto`/`FournisseurDto`
(legacy) — contrairement aux autres endpoints `/clients/*`/`/fournisseurs/*`. Assumé
délibérément : ces deux routes (`/{id}/photo`) sont entièrement nouvelles (l'ancien mécanisme
utilisait une URL totalement différente, `/save/{id}/{title}/{context}`), donc aucun contrat
existant à préserver byte-for-byte, contrairement à `/utilisateurs/*` en Phase 4a.

## 3. Infrastructure de test

`AbstractIntegrationTest` étendu avec un conteneur MinIO singleton, même pattern que le conteneur
PostgreSQL déjà en place (`org.testcontainers:minio`, ajouté en scope test).

## 4. Étape 0 — caractérisation

Aucun test n'existait sur l'ancien mécanisme (`PhotoController`/`StrategyPhotoContext`/
`FlickrService`/les 5 `Save*Photo`) — confirmé par recherche exhaustive avant de commencer, rien à
préserver via golden master. Nouveau fichier
`media/PhotoAttachmentCharacterizationTest.java` (6 tests) :
- Upload retourne une URL publiquement accessible (vérifié par un vrai `HttpClient.GET` sur
  l'URL retournée, pas juste une assertion sur la forme de la chaîne).
- Rattachement réussi sur chacune des 5 entités (Article, Client, Fournisseur, Tenant, User).
- **Test le plus important** (`attachingPhotoToUserDoesNotCorruptPasswordOrRejectAsDuplicate`) :
  reproduit exactement le scénario du bug §0 et prouve qu'il n'existe plus — upload de photo sur
  un utilisateur puis connexion réussie avec le mot de passe **original** (fourni à
  l'inscription), preuve directe d'absence de corruption.

## 5. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Mécanisme transversal, pas dupliqué par module | **Vérifié** — `MediaStorageService` unique, 5 classes `Save*Photo` supprimées. |
| MinIO remplace Flickr entièrement | **Vérifié** — `flickr4java` retiré de `pom.xml`, `FlickrService`/`FlickrServiceImpl`/`FlickrConfiguration` supprimés. |
| Bug mot de passe/doublon corrigé comme conséquence du redesign | **Vérifié** — `UserService.updatePhoto()` dédié, testé explicitement. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié** — 6/6 et 2/2, aucune violation de frontière malgré les 5 nouveaux consommateurs de `media.application.dto`. |
| Tous les tests verts | **Vérifié** — `./mvnw clean verify` : 237 tests (231 + 6 nouveaux), 0 échec, 0 erreur. |

## 6. Fichiers modifiés/créés

**Production, module `media` (nouveau)** : `media/application/MediaStorageService.java` (+
`impl/MinioMediaStorageService.java`), `media/application/dto/{PhotoUrlRequest,MediaUploadResponse}.java`
(+ `package-info.java`), `media/presentation/rest/MediaController.java`.

**Production, supprimés** : `controller/PhotoController.java`, `controller/api/PhotoApi.java`,
`services/FlickrService.java`, `services/impl/FlickrServiceImpl.java`, `config/FlickrConfiguration.java`,
`services/strategy/{Strategy,StrategyPhotoContext,SaveArticlePhoto,SaveClientPhoto,
SaveFournisseurPhoto,SaveEntreprisePhoto,SaveUtilisateurPhoto}.java`.

**Production, étendus (`updatePhoto`)** : `catalog.ArticleService(Impl)`,
`sales.CustomerService(Impl)`, `purchasing.SupplierService(Impl)`, `tenant.TenantService(Impl)`,
`identity.UserService(Impl)`, + les 5 contrôleurs (2 nouveaux : `ClientPhotoLegacyController`,
`FournisseurPhotoLegacyController`).

**Config** : `pom.xml` (flickr4java → io.minio + testcontainers minio), `application.yml`
(clés Flickr en clair → `minio.*` via env vars), `.env.example` (4 nouvelles variables `MINIO_*`).

**Test** : `support/AbstractIntegrationTest.java` (conteneur MinIO), `media/PhotoAttachmentCharacterizationTest.java`
(nouveau, 6 tests).

**Docs** : `docs/migration-notes.md`.

## 7. Prochaine étape

4c terminé et vérifié vert sur son périmètre. **N'enchaîne pas sur un autre incrément sans feu
vert explicite.**
