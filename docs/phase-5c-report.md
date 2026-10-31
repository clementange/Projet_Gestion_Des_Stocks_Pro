# Phase 5c — RBAC sur les écritures — Rapport

## ⚠ Changement de comportement, à lire avant déploiement

**Les utilisateurs non-admin existants perdent l'accès en écriture tant qu'aucun rôle ne leur est
attribué.** `V8__seed_write_permissions.sql` n'accorde les 34 nouvelles permissions qu'au rôle
`ADMINISTRATEUR`. Un utilisateur qui pouvait hier créer un article ou valider une commande — parce
qu'aucune permission n'était vérifiée — recevra désormais un 400 `*_ACCESS_DENIED`. C'est l'objet
même de l'incrément, mais c'est une rupture fonctionnelle : il faut attribuer des rôles avant la
mise en service. Les quatre rôles métier recommandés sont décrits dans `docs/rbac-matrix.md` §5.

## 1. Investigation

Jusqu'en 5b-2h, **l'appartenance** au tenant était vérifiée partout, mais pas le **droit métier**.
Conséquence directe : tout membre d'une organisation pouvait y créer, modifier et supprimer
n'importe quelle ressource. L'inventaire exhaustif des endpoints d'écriture (lu depuis le
`RequestMappingHandlerMapping`, pas par grep) donne **59 endpoints** :

| État avant 5c | Nombre | Détail |
|---|---|---|
| Déjà gardés par une permission | 12 | `STOCK_RECEIVE/ISSUE/CORRECT` (5b-2d), `SALE_CREATE`, `CUSTOMER_ORDER_RESERVE/DELIVER/CANCEL`, `PURCHASE_ORDER_RECEIVE`, `STOCK_TRANSFER_SHIP/RECEIVE`, les 2 routes RBAC (`RBAC_MANAGE`) |
| **Aucune permission** | **34** | les 6 référentiels × create/update/delete, site create/delete, user create/delete, les 3 créations d'agrégat, les 9 transitions d'état restantes |
| Sans permission, à juste titre | 13 | amorçage public, self-only, upload, photos, legacy (détail en §5) |

**Découverte notable : 9 transitions d'état n'étaient gardées que par l'appartenance.** `valider`,
`préparer`, `expédier` (commande client), `valider`, `annuler` (commande fournisseur), `demander`,
`approuver`, `préparer`, `annuler` (transfert) ne vérifiaient *aucune* permission, alors que leurs
voisines immédiates (`réserver`, `livrer`, `expédier` d'un transfert) en avaient une. L'investigation
initiale ne les avait pas annoncées : elles sont apparues en construisant l'inventaire automatique,
et non en lisant le périmètre annoncé.

## 2. Décisions de conception

### Ordre imposé : appartenance → permission → écriture

L'appartenance répond à « cette ressource est-elle dans mon organisation ? » et renvoie un
`*_NOT_FOUND` **masqué**. La permission répond à « ai-je le droit de faire cela ? » et renvoie un
`*_ACCESS_DENIED`. L'ordre inverse ferait fuiter l'existence d'une ressource d'autrui par le code
d'erreur, et surtout il réintroduirait le contournement de 5b-2b : une affectation de portée
`GLOBAL` matche n'importe quel `scopeId`, donc `hasPermission` seul ne prouve jamais l'isolation.

**J'ai commis exactement cette erreur** : les 7 `delete` ont d'abord reçu le contrôle de permission
*avant* la vérification d'appartenance. Corrigé avant tout commit, et c'est la raison pour laquelle
l'ordre est désormais écrit dans `CLAUDE.md` et commenté dans chaque méthode concernée.

### Le contrôle vit dans le service, pas dans le contrôleur

`userId` et `organizationId` arrivent en **paramètres** (`@AuthenticationPrincipal` résolu dans
l'adaptateur web), jamais via `SecurityContextHolder` — règle d'architecture du dépôt. Les six
services de référentiel reçoivent un `requirePermission(userId, code, organizationId)` privé ;
`CustomerOrder`, `PurchaseOrder` et `StockTransfer` ont un helper équivalent de portée `SITE`,
extrait de la forme déjà présente dans `receiveLine`/`ship` plutôt que réécrit.

### Portée de chaque permission

`ORGANIZATION` pour les référentiels, `SITE` pour tout ce qui touche au stock. Deux arbitrages :

- **`SITE_DELETE` est de portée `SITE`** (sur le site visé), alors que `SITE_CREATE` est de portée
  `ORGANIZATION` : supprimer *un* site est une opération locale, créer un site ne peut pas l'être
  puisque le site n'existe pas encore.
- **Les transitions de transfert sont évaluées sur le site d'origine**, sauf
  `STOCK_TRANSFER_RECEIVE` (destination). Critère : le site évalué est celui qui porte la
  conséquence sur le stock — l'origine se désengage, la destination encaisse.

### L'amorçage d'un tenant ne peut pas être gardé

`TenantRegistrationServiceImpl` crée l'admin **avant** qu'aucune affectation RBAC n'existe : à cet
instant aucune permission n'est vérifiable. `UserService.save(dto)` reste donc sans garde, et
`save(dto, userId, organizationId)` — gardée par `USER_CREATE` — est la variante utilisée par le
point d'entrée HTTP. Gater `save(dto)` directement aurait cassé l'inscription d'un tenant.
**Alternative écartée** : un drapeau « bootstrap » passé au service, qui aurait mis une porte
dérobée paramétrable sur le chemin de création d'utilisateur.

### DÉCISION PAR DÉFAUT : aucun rôle métier n'est seedé

Découper les responsabilités d'une entreprise est une décision produit. Seeder « magasinier » ou
« commercial » donnerait des droits à des utilisateurs sur la seule base d'une supposition. V8
n'accorde donc les permissions qu'à `ADMINISTRATEUR` (leçon de V7 : sans cela, tout admin existant
serait immédiatement bloqué), et les quatre rôles standards sont **documentés comme
recommandation** dans `docs/rbac-matrix.md`, pas créés.

## 3. Ce qui a changé

| Fichier | Changement |
|---|---|
| `V8__seed_write_permissions.sql` (nouveau) | 34 permissions, toutes accordées à `ADMINISTRATEUR`, idempotent |
| `ErrorCodes` | 7 codes `*_ACCESS_DENIED` (33000-33006) pour les ressources qui n'en avaient pas |
| `Article/Category/Customer/Supplier/City/Warehouse ServiceImpl` | `save/update/delete` prennent `userId`, vérifient la permission **après** l'appartenance |
| `SiteServiceImpl` | `SITE_CREATE` (ORGANIZATION), `SITE_DELETE` (SITE) |
| `UserServiceImpl` | surcharges gardées `save(dto, userId, orgId)` / `delete(id, tenantId, userId, orgId)` ; variantes non gardées conservées pour l'amorçage et le legacy |
| `CustomerOrder/PurchaseOrder/StockTransfer ServiceImpl` | création + **9 transitions** gardées, helper de portée SITE extrait |
| 10 contrôleurs | propagent `principal.getIdUtilisateur()` |
| `AbstractIntegrationTest` | `userWithAllWritePermissions(organizationId)` pour les décors de test |
| `docs/rbac-matrix.md` (nouveau) | matrice ressource × action × portée, exclusions justifiées, rôles proposés, procédure d'ajout |

## 4. Tests

### `WriteEndpointPermissionGateTest` — l'inventaire (5 tests)

Même esprit que `CreateEndpointIdGuardTest`, en quatre niveaux :

1. l'ensemble des endpoints d'écriture **réellement exposés** doit être exactement `GATED ∪
   EXCLUDED` — un endpoint neuf non classé fait échouer le build ;
2. chaque classe déclarée gardienne appelle vraiment `AuthorizationService.hasPermission`
   (bytecode, ArchUnit) ;
3. le code de permission déclaré pour un endpoint est présent dans la source qui l'applique —
   renommer ou retirer un code fait échouer le test ;
4. **tout code déclaré est accordé par une migration Flyway** — sans quoi la permission serait
   inobtenable et l'endpoint définitivement bloqué (le piège de V7, désormais verrouillé) ;
5. toute exclusion porte une justification non triviale.

Ce test a immédiatement payé : il a révélé que **`POST /auth/authenticate` manquait** à mon
inventaire manuel, et que la justification de `/utilisateurs/update/password` était trop maigre.

### `WritePermissionEnforcementHttpIntegrationTest` — l'application (5 tests)

Deux utilisateurs du **même tenant** : l'admin d'amorçage et un membre sans aucun rôle. Le test
vérifie explicitement que le membre est bien dans la même organisation que l'admin (sinon il
mesurerait l'appartenance au lieu de la permission), puis que le membre est refusé sur 8 créations
de référentiel, 3 créations d'agrégat, 1 mouvement de stock, 6 mises à jour, 5 suppressions et 2
transitions d'état — **avec le code `*_ACCESS_DENIED` attendu**, ce qui prouve au passage que
l'appartenance est bien passée *avant* la permission. L'admin, lui, réussit (le décor entier est
créé par lui, et une validation de commande est exercée explicitement).

### Honnêteté sur le rouge→vert

Le « rouge » est réel mais **collectif, pas nommé par ressource** : avant les correctifs, chacune
de ces requêtes renvoyait 200. Je n'ai pas conservé une exécution rouge par ressource ; j'ai
constaté le rouge sur l'ensemble en construisant l'inventaire puis le test d'application. C'est le
même écart qu'en 5b-2h, signalé plutôt que maquillé.

## 5. Tests existants modifiés

| Test | Modification | Justification |
|---|---|---|
| 12 tests d'intégration (≈120 sites d'appel) | ajout de `userId` aux appels d'écriture, via `userWithAllWritePermissions(orgId)` | adaptation mécanique de signature ; **aucune assertion touchée** |
| `CategoryServiceImplTest.shouldSaveCategoryWithSuccess`, `shouldThrowInvalidEntityException` (+ équivalents Article/Customer/Supplier) | créent désormais une organisation réelle et un utilisateur autorisé | ces tests appelaient `save()` sans organisation ; sans permission accordée, l'échec viendrait de la permission et **ces tests ne vérifieraient plus ce qu'ils annoncent** (validation). Rendre l'utilisateur autorisé préserve l'intention |
| `createArticle()` de 5 tests | crée sa propre organisation | le champ `organization` n'est renseigné que par `createSite()`, que les tests d'appartenance n'appellent pas → NPE |
| `StockTransferServiceIntegrationTest.runToShippable`, `CrossModuleEndToEndIntegrationTest` | accordent `STOCK_TRANSFER_APPROVE` à l'utilisateur approbateur | **changement de sémantique réel, pas une adaptation** : `approve(id, approverUserId, orgId)` fait de l'approbateur le sujet de la permission. C'est voulu |

`userWithAllWritePermissions` utilise une affectation de portée `GLOBAL` **dans une organisation
donnée** — exactement la forme de l'affectation d'amorçage. Elle ne peut donc pas masquer une
faille d'isolation inter-tenant, et `CrossTenantEndpointMatrixTest` reste vert.

## 6. Critères de sortie

| Critère | Statut |
|---|---|
| Toute écriture vérifie une permission, ou est exclue avec justification | **Vérifié** — 46 gardés / 13 exclus, test d'inventaire |
| Ordre appartenance → permission → écriture | **Vérifié** — relu méthode par méthode ; codes `*_ACCESS_DENIED` vs `*_NOT_FOUND` le prouvent à l'exécution |
| Contrôle dans le service, `userId`/`organizationId` en paramètres | **Vérifié** — aucun `SecurityContextHolder` hors adapter web |
| Migration Flyway accordant chaque permission à `ADMINISTRATEUR` | **Vérifié** — V8, + test qui échoue si on l'oublie |
| Aucun `V*.sql` existant édité | **Vérifié** — V8 est nouveau |
| Test paramétré énumérant les endpoints d'écriture | **Vérifié** — `WriteEndpointPermissionGateTest` |
| Refus d'un membre du même tenant sans permission ; admin OK | **Vérifié** — `WritePermissionEnforcementHttpIntegrationTest` |
| Matrice inter-tenant de 5b-2h toujours verte | **Vérifié** |
| Suite complète verte | **Vérifié** — 272 tests (262 avant), 0 échec, 0 erreur |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test désactivé, aucune assertion affaiblie | **Vérifié** — voir §5 |

## 7. Limites assumées

- **Pas de rouge nommé par ressource** (voir §4).
- **Les 3 endpoints `/{ressource}/{id}/photo`** (article, client, fournisseur) restent sans
  permission dédiée : appartenance seule. Introduire un `*_UPDATE_PHOTO` ou réutiliser
  `*_UPDATE` est une décision à prendre ; je ne l'ai pas tranchée seul, l'endpoint est listé en
  exclusion explicite et en backlog.
- **Les 3 routes legacy `/utilisateurs/**`** gardent l'appartenance mais pas la permission : elles
  n'ont pas de `userId` sous la main et doivent disparaître en 4e. Gap documenté, pas masqué.
- **`idEntreprise` de `UserDto` est encore fourni par le client** sur `/users/create` : un appelant
  peut créer un utilisateur dans le tenant d'autrui. Hors périmètre 5c (c'est un défaut
  d'appartenance, pas de permission) — **mis en backlog, gravité élevée**.
- **La portée des transitions n'est pas configurable** : « origine sauf réception » est codé en
  dur. Suffisant aujourd'hui, à revoir si un client veut séparer approbation et préparation.
- **Aucun test ne vérifie qu'un utilisateur avec *exactement* la permission requise passe** pour
  les 34 codes : les décors utilisent un utilisateur qui les a toutes. Les tests à permission
  unique existants (`grantOrderPermissions`, `grantTransferPermission`) ne couvrent que les 6
  permissions d'avant 5c.

## 8. Backlog découvert

- **`UserDto.idEntreprise` accepté du client** (voir §7) — création d'utilisateur inter-tenant.
  La matrice de 5b-2h ne le détecte pas : elle teste l'id de la **ressource**, pas ses **champs**.
- **Références imbriquées toujours non vérifiées** (déjà signalé en 5b-2g/2h) :
  `Article.category`, `CustomerOrder.customerId`, `PurchaseOrder.supplierId`, l'`article` des
  lignes. Un `customerId` d'un autre tenant passe encore.
- **Permission sur les photos** (voir §7).
- **Pas de permission de lecture** sur les référentiels : toute lecture reste ouverte à tout membre
  de l'organisation. Volontaire (5c porte sur les écritures), mais à trancher pour un rôle
  « lecture seule » réellement restrictif.
