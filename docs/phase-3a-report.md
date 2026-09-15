# Phase 3a — Rapport de sortie de phase

Premier incrément de Phase 3, le plus petit et le mieux compris : corriger
le cycle `catalog <-> purchasing`/`sales` détecté en Phase 1 et confirmé en
Phase 2 (`ArchitectureRulesTest.modules_must_be_free_of_cycles`), sans
toucher aux deux autres causes d'échec connues (injection par champ,
séparation JPA/domaine), volontairement laissées de côté pour 3b/3c.

## 1. Diagnostic avant correction

Racine unique : `catalog.application.impl.ArticleServiceImpl` injectait
directement `purchasing.infrastructure.persistence.PurchaseOrderLineRepository`,
`sales.infrastructure.persistence.SaleLineRepository` et
`CustomerOrderLineRepository` (repositories JPA internes à d'autres
modules), et manipulait directement leurs entités de domaine
(`PurchaseOrderLine`, `SaleLine`, `CustomerOrderLine`) dans quatre méthodes :
`findHistoriqueVentes`, `findHistoriaueCommandeClient` (faute de frappe),
`findHistoriqueCommandeFournisseur`, `delete`.

## 2. Option choisie, et pourquoi l'option (a) seule ne suffisait pas

Les deux options proposées dans `docs/migration-notes.md` (Phase 1) étaient :
(a) exposer `findLinesByArticleId` sur les facades publiques
`SaleService`/`CustomerOrderService`/`PurchaseOrderService`, ou (b) déplacer
les méthodes d'historique hors de `catalog`.

**Constat empirique, vérifié avant tout commit définitif** : l'option (a)
seule ne suffit pas. `sales` et `purchasing` dépendent déjà légitimement de
`catalog` (`ArticleDto`) pour leurs propres lignes de commande/vente. Tant
que `catalog` a une raison quelconque de dépendre de `sales`/`purchasing` —
même strictement via leur façade publique, sans toucher à leurs internes —
le graphe de dépendance entre modules reste cyclique
(`catalog -> sales -> catalog`), parce que le graphe de cycles de Spring
Modulith/ArchUnit se calcule au niveau du **module**, pas au niveau de la
couche (`application` vs `domain` vs `infrastructure`) à l'intérieur du
module. J'ai implémenté l'option (a), relancé
`ArchitectureRulesTest.modules_must_be_free_of_cycles`, constaté qu'elle
échouait encore avec exactement les 4 mêmes cycles, et j'ai donc combiné
(a) + (b) :

1. **(a) appliquée** : `SaleService.findLinesByArticleId`,
   `CustomerOrderService.findLinesByArticleId`,
   `PurchaseOrderService.findLinesByArticleId` ajoutées aux façades
   publiques des trois modules (retournent `SaleLineDto`/
   `CustomerOrderLineDto`/`PurchaseOrderLineDto`, pas les entités de
   domaine) — nécessaire de toute façon pour que (b) ait une API propre à
   consommer.
2. **(b) appliquée** : les 3 méthodes d'historique sont déplacées hors de
   `catalog`, vers des contrôleurs REST dédiés vivant dans les modules qui
   possèdent réellement la donnée :
   - `sales.presentation.rest.legacy.SalesArticleHistoryLegacyController`
     — sert `/articles/historique/vente/{id}` et
     `/articles/historique/commandeclient/{id}`.
   - `purchasing.presentation.rest.legacy.PurchaseOrderArticleHistoryLegacyController`
     — sert `/articles/historique/commandefournisseur/{id}`.

   **URL HTTP strictement inchangées** — seul l'emplacement du code source
   change. Chaque contrôleur combine la source legacy (repository plat
   `dto`/`repository`, non concerné par la règle de frontière de module,
   Phase 1) et la source module neuf (via la façade publique), exactement
   comme le faisait `ArticleServiceImpl` avant.
3. **`delete()` : ni (a) ni (b) ne suffisaient**. Le garde-fou de
   suppression a besoin de savoir, DEPUIS `catalog`, si un article est
   utilisé ailleurs — question qui ne peut être répondue sans que `catalog`
   dépende de `sales`/`purchasing`, quel que soit l'endroit où le code vit.
   Solution retenue : s'appuyer sur la contrainte `FOREIGN KEY
   (article_id) REFERENCES article(id)` déjà présente en base sur
   `sale_line`/`customer_order_line`/`purchase_order_line`
   (`V1__initial_schema.sql`, confirmée avant modification) — Postgres
   refuse déjà nativement la suppression d'un article référencé. Le code
   catch désormais `DataIntegrityViolationException` autour de
   `articleRepository.deleteById(id)` et la traduit en
   `InvalidOperationException(ARTICLE_ALREADY_IN_USE)`, au lieu d'appeler
   `saleService.findLinesByArticleId(...)` etc. avant la suppression. Les
   pré-vérifications contre les repositories **legacy** (`venteRepository`,
   `commandeClientRepository`, `commandeFournisseurRepository`) restent
   inchangées — ce ne sont pas des modules au sens de la règle (Phase 1),
   pas de cycle possible de ce côté.

## 3. Corrections annexes demandées

- Faute de frappe `findHistoriaueCommandeClient` -> `findHistoriqueCommandeClient`
  corrigée dans `ArticleService`, l'ancien `ArticleServiceImpl` (avant
  déplacement) et le nouveau `SalesArticleHistoryLegacyController`. **Le
  chemin HTTP lui-même ne contenait jamais la faute** (`@GetMapping(...
  "/articles/historique/commandeclient/{idArticle}")`) — seul l'identifiant
  de méthode Java était fautif ; correction sans aucun impact sur le
  contrat externe.
- `@NamedInterface` : ajout initial sur `purchasing.application` et
  `purchasing.application.dto` (nécessaire tant que l'option (a) seule
  était en jeu), puis **retiré** une fois (b) appliqué — plus aucun module
  autre que `purchasing` lui-même ne consomme `PurchaseOrderService`/
  `PurchaseOrderLineDto` après le déplacement. Cohérent avec la méthode
  Phase 1 ("exposer exactement ce qui est prouvé nécessaire").
- Deux classes nommées `ArticleHistoryLegacyController` (une dans `sales`,
  une dans `purchasing`) auraient collisionné sur le nom de bean Spring
  par défaut (dérivé du simple nom de classe) — renommées
  `SalesArticleHistoryLegacyController` et
  `PurchaseOrderArticleHistoryLegacyController` avant que le problème ne se
  manifeste au démarrage du contexte.

## 4. Tests de non-régression ajoutés

Aucun test existant ne couvrait les méthodes touchées (confirmé avant de
commencer : recherché dans `ArticleServiceImplTest` et dans tous les tests
de caractérisation Phase 0 — zéro résultat sur `articles/delete` ou
`articles/historique`). Code de production touché pour la première fois
dans cette migration sans filet de sécurité préexistant : 5 tests ajoutés
dans `src/test/java/.../legacy/ArticleHistoryLegacyEndpointsTest.java`
(nouveau fichier, style aligné sur les `*CharacterizationTest` de Phase 0) :

1. `findHistoriqueVentesMergesLegacyAndNewModuleLines` — vente créée via le
   module neuf, vérifie qu'elle apparaît dans `/articles/historique/vente/{id}`.
2. `findHistoriqueCommandeClientMergesLegacyAndNewModuleLines` — idem pour
   `/articles/historique/commandeclient/{id}`.
3. `findHistoriqueCommandeFournisseurMergesLegacyAndNewModuleLines` — idem
   pour `/articles/historique/commandefournisseur/{id}`.
4. `deleteArticleUsedInCommandeFournisseurIsRejected` — confirme que le
   garde-fou base sur la contrainte FK fonctionne toujours (400
   `ARTICLE_ALREADY_IN_USE`) pour un article référencé par une commande
   fournisseur créée via le module neuf.
5. `deleteUnusedArticleStillSucceeds` — confirme l'absence de faux positif
   (suppression d'un article non utilisé toujours acceptée).

Tous verts (`Tests run: 5, Failures: 0, Errors: 0`).

## 5. Critère de sortie — vérifié

```
ModularityTests            : 2/2 verts (verifiesModularStructure, createModuleDocumentation)
ArchitectureRulesTest.modules_must_be_free_of_cycles : VERT
```

Confirmé par exécution ciblée puis par `./mvnw clean verify` complet :

```
Tests run: 129, Failures: 2, Errors: 0
BUILD FAILURE
```

Les 2 échecs restants sont **exactement** ceux déjà connus et
explicitement hors périmètre de 3a, inchangés par cet incrément :
- `ArchitectureRulesTest.domain_must_not_depend_on_jakarta_persistence`
  (272 violations, 21/21 entités — périmètre de 3c).
- `ArchitectureRulesTest.no_field_injection_anywhere` (16 violations,
  0 dans les modules neufs — périmètre de 3b).

Aucune autre régression : les 117 tests de caractérisation/intégration de
Phase 0 restent verts sans qu'aucune de leurs assertions n'ait été
modifiée (5 nouveaux tests ajoutés, 0 modifié). Le nombre total de tests
passe de 124 (fin Phase 2) à 129.

## 6. Fichiers modifiés

**Production** :
- `catalog/application/ArticleService.java`, `catalog/application/impl/ArticleServiceImpl.java`,
  `catalog/presentation/rest/ArticleController.java` — 3 méthodes
  d'historique retirées, `delete()` reécrit (contrainte FK), plus aucune
  dépendance vers `sales`/`purchasing`.
- `sales/application/SaleService.java` (+`SaleServiceImpl`),
  `sales/application/CustomerOrderService.java` (+`CustomerOrderServiceImpl`),
  `purchasing/application/PurchaseOrderService.java` (+`PurchaseOrderServiceImpl`)
  — méthode `findLinesByArticleId` ajoutée à chaque façade.
- `sales/presentation/rest/legacy/SalesArticleHistoryLegacyController.java` (nouveau).
- `purchasing/presentation/rest/legacy/PurchaseOrderArticleHistoryLegacyController.java` (nouveau).

**Tests** :
- `legacy/ArticleHistoryLegacyEndpointsTest.java` (nouveau, 5 tests).

**Docs** :
- `docs/migration-notes.md` — les deux entrées Phase 1/Phase 2 concernant
  ce cycle marquées résolues, avec le récit de pourquoi (a) seule ne
  suffisait pas.

## 7. Zones d'ombre / décisions prises en cours de route

Aucune ambiguïté fonctionnelle bloquante. Un point mérite une validation
explicite avant de considérer le pattern comme définitif :

- **Le garde-fou de suppression dépend désormais d'un comportement de la
  base de données** (contrainte FK) plutôt que d'une vérification
  applicative explicite pour la partie "modules neufs". C'est cohérent
  avec ce que Phase 0 avait déjà caractérisé comme le comportement réel
  d'autres endpoints (codes dupliqués -> 500 par contrainte UNIQUE, jamais
  vérifiés applicativement), mais c'est un choix de conception qui mérite
  d'être noté : si un jour `sale_line`/`customer_order_line`/
  `purchase_order_line` passaient à `ON DELETE CASCADE` ou `SET NULL`, ce
  garde-fou cesserait silencieusement de fonctionner pour la partie
  modules neufs (les checks legacy resteraient, eux, inchangés). À garder
  en tête pour toute future migration de schéma touchant ces tables.

## 8. Prochaine étape

Incrément 3a terminé et vérifié vert sur son périmètre. **Ne pas enchaîner
sur 3b (injection par champ) sans feu vert explicite**, conformément à la
règle de travail.
