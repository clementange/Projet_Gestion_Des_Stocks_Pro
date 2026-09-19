# Phase 5b-2a — lecture scoping par organisation, entités à référence directe — Rapport

Premier des trois incréments de 5b-2 (lecture non filtrée, découpée par forme de dérivation
d'organisation — voir docs/phase-5b1-report.md §8). Couvre les 4 entités dont l'organisation est
directement portée par l'entité elle-même : `Article`, `Category` (référence `@ManyToOne
Organization`), `Customer`, `Supplier` (`Long organizationId`).

## 0. Root cause additionnelle trouvée en implémentant

Avant ce correctif, `ArticleController.save()`/`CategoryController.save()`/
`CustomerController.create()`/`SupplierController.create()` ne fixaient jamais
l'organisation/organizationId côté serveur — elles faisaient confiance à ce que le client
fournissait dans le corps de la requête (ou rien du tout). Une fois la lecture filtrée par
organisation, toute entité créée sans organisation explicite serait devenue invisible à son
propre créateur, cassant le flux le plus élémentaire (créer puis relire). Confirmé en pratique :
les tests d'intégration existants qui créent puis relisent immédiatement un article/une catégorie
sans jamais fournir d'organisation dans le JSON (`ArticleHistoryLegacyEndpointsTest`,
`BusinessModulesHttpIntegrationTest`, `CustomerSupplierControllerCharacterizationTest`,
`PhotoAttachmentCharacterizationTest`) ont tous échoué en 404 lors du premier passage de la suite
complète après l'implémentation du filtrage seul. Corrigé en forçant l'organisation depuis
`principal.getOrganizationId()` côté contrôleur, **toujours en écrasant** ce que le client aurait
pu fournir — pas seulement en cas d'absence, pour empêcher un appelant d'injecter des données dans
une autre organisation en fournissant un id arbitraire.

Ce correctif est une conséquence nécessaire et bornée du filtrage en lecture (sans lui, l'incrément
serait incohérent), pas une extension du périmètre vers la sécurisation des écritures en général —
l'absence de toute vérification de permission sur `create`/`update`/`delete` de ces 4 entités reste
un gap RBAC déjà catalogué (voir docs/phase-5b1-report.md §8), non traité ici.

## 1. Conception

Pour chaque entité : `findAll`, `findById`, `findByCode` (Article/Category) gagnent un paramètre
`organizationId`, résolu dans le contrôleur depuis `principal.getOrganizationId()` (disponible
depuis Phase 5b-1). Règle de correspondance, symétrique aux deux bouts : le match exige que
l'`organizationId` de l'appelant **et** celui de l'entité soient tous deux non-null et égaux — un
`null` d'un côté ou de l'autre ne matche jamais par coïncidence.

- `findAll` : filtre la liste.
- `findById`/`findByCode` : sur mismatch, lève **la même** exception `*_NOT_FOUND` qu'un id
  vraiment inexistant — pas de code distinct, pour qu'une lecture cross-organisation soit
  indiscernable d'un id qui n'existe pas (aucune fuite d'existence).

`findById(Long id)` (sans organisation) est conservé tel quel pour `ArticleServiceImpl.updatePhoto`/
`CustomerServiceImpl.updatePhoto`/`SupplierServiceImpl.updatePhoto`, qui l'utilisaient déjà en
interne — ces méthodes d'écriture ne sont pas dans le périmètre de cet incrément (même raisonnement
que ci-dessus : le filtrage en lecture ne doit pas silencieusement étendre son périmètre à
l'écriture générale). `CategoryServiceImpl` n'avait aucun appelant interne de ce type, donc ses
trois méthodes ont été changées directement, sans variante non filtrée à conserver.

Vérifié avant implémentation : aucun autre module n'appelle `ArticleService`/`CategoryService`/
`CustomerService`/`SupplierService.findById`/`findAll`/`findByCode` en interne (recherche
exhaustive) — changer ces signatures ne casse aucun consommateur cross-module.

## 2. Tests

Chaque entité gagne un test `crossOrganizationReadsAreScoped` (dans les fichiers
`*ServiceImplTest` déjà existants) : crée une organisation A et B, une entité dans A, vérifie que
la lecture depuis A réussit normalement (`findById`/`findByCode`/`findAll`) et que depuis B,
`findById`/`findByCode` lèvent `EntityNotFoundException` (même comportement qu'un id inexistant)
et que `findAll` ne renvoie rien.

Les tests existants qui appelaient les signatures désormais changées (`ArticleServiceImplTest`,
`CategoryServiceImplTest`) ont été adaptés mécaniquement (paramètre `organizationId` ajouté aux
appels), sans changement de ce qu'ils vérifient.

## 3. Critère de sortie

| Critère | Résultat |
|---|---|
| `findAll`/`findById`/`findByCode` filtrés par organisation, pour les 4 entités | **Vérifié** — 4 tests dédiés. |
| Mismatch = même 404 qu'un id inexistant, pas de fuite d'existence | **Vérifié**. |
| Création force l'organisation depuis l'appelant, jamais depuis le client | **Vérifié** — confirmé par la régression détectée puis corrigée (voir §0). |
| `updatePhoto` (hors périmètre) toujours fonctionnel | **Vérifié** — `findById(Long)` interne conservé, `PhotoAttachmentCharacterizationTest` vert. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 210 tests, 0 échec, 0 erreur (206 avant l'incrément, +4 tests dédiés). |

## 4. Fichiers modifiés

**Production** : `catalog/application/{ArticleService,CategoryService}.java`,
`catalog/application/impl/{ArticleServiceImpl,CategoryServiceImpl}.java`,
`catalog/presentation/rest/{ArticleController,CategoryController}.java`,
`sales/application/CustomerService.java`, `sales/application/impl/CustomerServiceImpl.java`,
`sales/presentation/rest/CustomerController.java`,
`purchasing/application/SupplierService.java`,
`purchasing/application/impl/SupplierServiceImpl.java`,
`purchasing/presentation/rest/SupplierController.java`.

**Test** : `catalog/ArticleServiceImplTest.java`, `catalog/CategoryServiceImplTest.java`,
`sales/CustomerServiceImplTest.java`, `purchasing/SupplierServiceImplTest.java`.

**Docs** : `docs/migration-notes.md`.

## 5. Prochaine étape

5b-2a terminé et vérifié vert. Restent 5b-2b (dérivation via Site : `Sale`, `CustomerOrder`,
`PurchaseOrder`, `StockTransfer`, `Site`, `Stock`) et 5b-2c (cas spéciaux : `Organization`/`Tenant`
"soi-même uniquement", `User` via `Tenant.id`, `Role`/`Permission`). **N'enchaîne pas sur un autre
incrément sans feu vert explicite.**
