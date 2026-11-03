# Phase 5e — Appartenance des références fournies par le client — Rapport

Ferme le point n°4 du backlog du Lot 1. Suite directe de la Phase 5d : même classe de bug,
appliquée à toutes les références métier au lieu du seul `UserDto.idEntreprise`.

## 1. La faille, reproduite avant tout correctif

Les endpoints d'écriture acceptaient des **références** (catégorie, client, fournisseur, article
d'une ligne) sans jamais vérifier qu'elles appartenaient à l'appelant. Le tenant B pouvait donc,
sur **son propre site** et **avec toutes ses permissions**, créer des agrégats rattachés aux
ressources du tenant A.

`ForeignReferenceInjectionTest` exécute les 11 sondes d'un coup et les rapporte toutes — découvrir
les fuites une par une aurait coûté un cycle de build par point d'injection. Résultat **avant**
correctif :

```
Le tenant B a pu utiliser 11 reference(s) du tenant A :
  - POST /articles/create (category de A)                      -> 200
  - PUT  /articles/update/{id} (category de A)                 -> 200
  - POST /customer-orders/create (customerId de A)             -> 200
  - POST /customer-orders/create (article de A dans une ligne) -> 200
  - POST /purchase-orders/create (supplierId de A)             -> 200
  - POST /purchase-orders/create (article de A dans une ligne) -> 200
  - POST /sales/create (article de A dans une ligne)           -> 400 (stock insuffisant : la
                                                                  reference etait acceptee)
  - POST /stock-transfers/create (article de A dans une ligne) -> 200
  - POST /stocks/article/{A}/site/{B}/entree                   -> 200
  - POST /stocks/article/{A}/site/{B}/sortie                   -> 200
  - POST /stocks/article/{A}/site/{B}/correction               -> (sonde invalide, voir §5)
```

Neuf injections en **HTTP 200** : elles réussissaient complètement.

Le cas `/stocks/.../entree` est le plus intéressant : l'appartenance du **site** était vérifiée
depuis 5b-2d, celle de l'**article** non. B créait donc un mouvement de stock sur l'article de A
vers son propre site — ce qui rendait au passage cet article de A **lisible** par B via
`GET /stocks/article/{id}/site/{id}`. Une écriture non vérifiée ouvrait une lecture.

### Pourquoi la matrice ne pouvait pas le voir

`CrossTenantEndpointMatrixTest` sonde l'`id` de la **ressource visée**, jamais la valeur d'un
**champ du corps** — limite déjà écrite dans son propre rapport en 5b-2h. Et
`hasPermission` passe légitimement : B a bien le droit de créer une commande sur son site.
C'est exactement l'angle mort identifié en 5d, ici traité pour toutes les ressources métier.

## 2. Correctif

Aucune nouvelle vérification n'a été écrite : les **finders déjà scopés** sont réutilisés
(`categoryService.findById(id, organizationId)`, `articleService.findById(...)`,
`customerService.findById(...)`, `supplierService.findById(...)`). Ils lèvent déjà le
`*_NOT_FOUND` masqué de l'entité référencée — c'est le code éprouvé de 5b-2a/2b, et il n'y a rien
de plus à maintenir.

| Endpoint | Référence désormais vérifiée |
|---|---|
| `POST /articles/create`, `PUT /articles/update/{id}` | `category` |
| `POST /customer-orders/create` | `customerId` + `article` de chaque ligne |
| `POST /purchase-orders/create` | `supplierId` + `article` de chaque ligne |
| `POST /sales/create` | `article` de chaque ligne |
| `POST /stock-transfers/create` | `article` de chaque ligne |
| `POST /stocks/.../entree|sortie|correction` | `idArticle` |

**Placement** : juste après la vérification d'appartenance du site, donc **avant** la permission —
tout le bloc d'appartenance reste groupé, conformément à `appartenance → permission → écriture`.

**Code d'erreur** : 404 avec le `*_NOT_FOUND` de l'entité **référencée**. C'est la convention
d'appartenance du dépôt, et elle est ici doublement juste : pour l'appelant, cette ressource
n'existe effectivement pas.

**Référence nulle** : laissée au validateur, pour qu'une catégorie ou un article absent garde son
message de validation d'origine plutôt que de devenir un 404 trompeur.

## 3. Un second défaut, trouvé en corrigeant le premier

Les nouvelles vérifications ont fait échouer 38 tests avec `Aucune category avec l'ID = … n'a ete
trouve`. Cause réelle, et c'est un vrai défaut, pas un artefact de test :

**`CategoryServiceImpl.save(dto, userId, organizationId)` n'appliquait pas `organizationId` à
l'entité créée.** C'est le **contrôleur** qui forçait `dto.setOrganization(...)`. Donc tout appelant
qui n'était pas le contrôleur — un autre service, un test, un futur batch — créait une ressource
**orpheline** (organisation nulle), invisible aux lectures scopées et inutilisable comme référence.
Un service qui reçoit `organizationId` sans l'appliquer a un contrat incohérent.

Corrigé dans les cinq services concernés (`Category`, `Article`, `Customer`, `Supplier`, `City`) :
l'organisation est imposée **dans le service**, depuis le paramètre. Les contrôleurs sont laissés
tels quels — ils écrivent la même valeur, la duplication est inoffensive et le service est
désormais autoritaire.

## 4. Tests

- **`architecture/ForeignReferenceInjectionTest`** (nouveau, 2 tests) : 11 sondes d'injection
  inter-tenant exécutées en bloc et rapportées ensemble, plus une **contre-épreuve** vérifiant que
  les mêmes endpoints fonctionnent avec les références de B et que les ressources de A restent
  intactes et lisibles par A.
- Suite complète : **276 tests** (274 avant), 0 échec.

## 5. Mon erreur de sonde, signalée

Ma sonde `correction` envoyait `?quantite=5` alors que l'endpoint attend `?delta=`. Elle échouait
donc en **400 (paramètre requis absent) avant d'atteindre le contrôleur** : elle ne prouvait rien,
ni en rouge ni en vert. Je l'ai découverte parce qu'elle restait rouge après le correctif. Corrigée
et re-vérifiée. Leçon : une sonde qui échoue pour une raison de protocole est un faux rouge, aussi
trompeur qu'un faux vert.

## 6. Tests existants modifiés

Aucune assertion affaiblie ; trois classes d'adaptation de **fixtures**, chacune révélant que la
fixture était déjà incorrecte.

| Fixture | Problème | Correction |
|---|---|---|
| `customerId(1L)` / `supplierId(1L)` dans 6 tests | l'id `1` ne désignait **aucune** entité réelle — la fixture s'appuyait sur l'absence de contrôle | création d'un vrai client / fournisseur du tenant |
| `createArticle()` créant sa **propre** organisation jetable (introduit par moi en 4e) | l'article n'appartenait pas à l'organisation de l'agrégat qui le référence | signature `createArticle(Long organizationId)` ; l'organisation devient explicite |
| `createArticle(orgB)` dans les tests d'appartenance | dans ces tests c'est le **site** qui est étranger, jamais l'article : l'article doit appartenir à l'appelant, sinon le test échoue pour la mauvaise raison et ne vérifie plus ce qu'il annonce | recentré sur `orgA` |

Deux maladresses de ma part pendant ces adaptations, corrigées avant le vert : une substitution
trop large a remplacé `organizationId` hors de sa portée dans des méthodes voisines, et un regex a
réécrit le nom de méthode du builder `.organizationId(` en `.organization.getId()(`.

## 7. Critères de sortie

| Critère | Statut |
|---|---|
| Faille reproduite par un test rouge avant correctif | **Vérifié** — 11 fuites listées en §1 |
| Toutes les références métier des écritures vérifiées | **Vérifié** — tableau §2 |
| Réutilisation des finders scopés, pas de contrôle neuf | **Vérifié** |
| Ordre appartenance → permission → écriture préservé | **Vérifié** |
| Contre-épreuve : les mêmes endpoints marchent avec ses propres références | **Vérifié** |
| Suite complète verte | **Vérifié** — 276 tests, 0 échec |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test désactivé, aucune assertion affaiblie | **Vérifié** — §6 |

## 8. Limites assumées

- **La matrice ne sonde toujours pas les champs du corps.** `ForeignReferenceInjectionTest` tient
  un inventaire **manuel** de 11 points d'injection ; rien ne fait échouer le build si un endpoint
  neuf introduit une douzième référence non vérifiée. Un test d'inventaire dérivé du
  `RequestMappingHandlerMapping`, comme `WriteEndpointPermissionGateTest` le fait pour les
  permissions, serait la vraie protection. **Reste au backlog.**
- **`UserRoleAssignment.userId` n'est pas vérifié** : l'admin de B peut attribuer un rôle de SON
  organisation à un utilisateur de A. L'`organizationId` étant forcé depuis le principal (5b-2f),
  ce n'est pas un vol inter-tenant mais une **auto-exposition** : B fait entrer un étranger chez
  lui. Gravité faible, hors périmètre, ajouté au backlog.
- **Les agrégats déjà créés avec des références étrangères ne sont pas détectés.** Le correctif
  ferme la porte, il ne fait pas l'inventaire du passé. Même réserve qu'en 5d : audit de données à
  prévoir si l'API a déjà été exposée.
- **Les mises à jour d'agrégat ne sont pas concernées** : il n'existe aucun endpoint de
  modification de ligne de commande, seulement des transitions d'état sur des lignes déjà
  enregistrées. Vérifié, rien à faire.
