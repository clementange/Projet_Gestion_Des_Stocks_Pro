# Lot 1 — Rapport final

Six incréments exécutés de bout en bout, chacun vert, commité sans signature et poussé avant le
suivant : **L0**, **5b-2g**, **5b-2h**, **5b-2i**, **5c**, **4e**. Aucune Phase 6 ni 7 n'a été
démarrée.

## 1. Ce que le lot a fermé

| Incrément | Objet | Rapport | Tests à la sortie |
|---|---|---|---|
| **L0** | chaîne Java 21 + Lombok 1.18.46 + JaCoCo 0.8.16 + enforcer | `phase-L0-report.md` | 255 |
| **5b-2g** | « un create n'est jamais un update » sur les 10 derniers endpoints | `phase-5b2g-report.md` | 255 |
| **5b-2h** | appartenance sur tous les delete/update + **matrice inter-tenant automatique** | `phase-5b2h-report.md` | 257 |
| **5b-2i** | plus aucune valeur par défaut sensible, démarrage fail-fast | `phase-5b2i-report.md` | 262 |
| **5c** | permission RBAC sur les 46 endpoints d'écriture | `phase-5c-report.md` | 272 |
| **4e** | suppression des cinq paquets plats legacy | `phase-4e-report.md` | 271 |

Documents transverses produits : `docs/rbac-matrix.md` (matrice ressource × action × portée et
procédure d'ajout d'une permission) et `docs/api-migration-map.md` (destination des 43 classes
plates).

## 2. Les trois résultats qui comptent

### La matrice inter-tenant a trouvé 30 fuites là où l'analyse manuelle en prévoyait 7

Écrite **avant** les correctifs, `CrossTenantEndpointMatrixTest` (71 sondes + 11 contrôles
d'effet de bord) a révélé que **toutes** les actions de machine à états étaient exploitables d'un
tenant à l'autre, que les lectures de `City` et `Warehouse` n'avaient jamais été scopées, et que
`DELETE /user-role-assignments/{id}` laissait n'importe quel admin de tenant supprimer
l'affectation RBAC d'un autre. C'est le livrable le plus rentable du lot : il transforme une
revue ponctuelle en garde-fou permanent.

### Une seule classe de bug explique presque tout

`hasPermission` ne prouve **jamais** l'isolation inter-tenant : une affectation de portée `GLOBAL`
matche n'importe quel `scopeId`. Chaque fuite trouvée en 5b-2b, 5b-2f, 5b-2h et 5c relève du même
motif — une vérification de **droit** utilisée à la place d'une vérification d'**appartenance**.
D'où l'ordre désormais imposé partout et inscrit dans `CLAUDE.md` : **appartenance → permission →
écriture**, avec `*_NOT_FOUND` masqué d'un côté et `*_ACCESS_DENIED` de l'autre. J'ai moi-même
inversé cet ordre dans les sept `delete` de 5c avant de le corriger : la règle est utile.

### Les paquets plats masquaient deux cycles réels

Exclus du graphe de modules, ils suffisaient à cacher une dépendance circulaire. Les vider a
révélé `identity ↔ tenant` et `catalog ↔ legacy`, tous deux rejetés au premier run par
`ModularityTests`. Le second est résolu proprement par inversion de dépendance
(`catalog.domain.port.ArticleUsagePort`).

## 3. Décisions par défaut prises sans arbitrage humain

| Décision | Motif |
|---|---|
| **Aucun rôle métier seedé** (5c) — seul `ADMINISTRATEUR` reçoit les 34 permissions | découper les responsabilités d'une entreprise est une décision produit ; les 4 rôles standards sont *documentés*, pas créés |
| **`JDK 21` déclaré chaîne de référence** (L0) | sous JDK 25, ArchUnit 1.3.1 dégrade son analyse (342 avertissements `falling back to simple import`) |
| **Défaut vide plutôt que placeholder absent** pour les secrets (5b-2i) | permet de lister *toutes* les variables manquantes d'un coup avec un message actionnable |
| **`tenants/delete` conservé** (5b-2h) | déjà self-only ; le supprimer serait un changement de contrat sans gain de sécurité — contraire au défaut suggéré, consigné |
| **Cluster historique déplacé, pas supprimé** (4e) | les tables plates portent des données que les tables des modules n'ont pas |
| **`ApplicationUserDetailsService` dans `config/`** (4e) | il compose `identity` et `tenant` ; l'y mettre créerait un cycle |

## 4. Erreurs que j'ai commises, et ce qu'elles ont coûté

Elles sont détaillées dans chaque rapport ; la liste sert à ne pas les répéter.

| Erreur | Détection | Leçon |
|---|---|---|
| **Faux vert L0** : « build vert sous JDK 25 » annoncé après un seul run | JaCoCo 0.8.12 ne sait pas instrumenter Java 25 et tuait le contexte Spring de façon non déterministe | pour une propriété de chaîne d'outils, **un run vert n'est pas une preuve** |
| **Permission avant appartenance** dans les 7 `delete` de 5c | relecture méthode par méthode avant commit | l'ordre est la règle, pas une préférence |
| **Plan corrigé par le code** : 9 entités annoncées supprimables, en réalité une seule pièce | lecture des mappings JPA après celle des importateurs | pour une entité JPA, les importateurs ne suffisent pas — lire les **relations** |
| **`git add -A` interrompu** : le commit `0276452` ne contient que 4 renommages, sans les 79 imports — il ne compile pas | `git show --stat` après le push | corrigé par `ebd0196` ; signalé plutôt que l'historique réécrit |
| **Faux positif de matrice** : marqueur `"id":2,` matchant un `organization.id` imbriqué | la matrice échouait sur un endpoint correct | un marqueur de test doit être non ambigu |
| **Test invisible au grep** : un upsert appelant `save()` deux fois sur le même objet | échec en suite complète | chercher un motif par sa **forme**, pas par un fragment de syntaxe |
| **`{{id}}` laissé littéral** dans 6 `@PutMapping` | 255 tests en échec au démarrage | — |

## 5. État de sortie, vérifié

| Vérification | Résultat |
|---|---|
| `./mvnw clean verify` | **BUILD SUCCESS — 271 tests, 0 échec, 0 erreur** |
| `ArchitectureRulesTest` / `ModularityTests` | verts — 7 règles (6 en entrée de lot) |
| `git status` | propre |
| `git log origin/main..` | vide — tout est poussé |
| Commits du lot | 14, **aucun signé**, aucun `--force`, aucun `--no-verify` |
| Aucun `@Disabled`, aucune assertion affaiblie | vérifié ; chaque test existant modifié est justifié dans son rapport |
| Migrations Flyway | V7 et V8 ajoutées ; **aucun `V*.sql` existant édité** |

## 6. Ce qui reste ouvert — à trancher avant la suite

Par gravité décroissante :

1. **⚠ `UserDto.idEntreprise` est accepté du client** sur `POST /users/create` : un appelant peut
   créer un utilisateur dans le tenant d'autrui. C'est un défaut d'**appartenance**, hors
   périmètre 5c, et la matrice ne le détecte pas (elle teste l'id de la ressource, pas ses
   champs). **Gravité élevée.**
2. **⚠ L'`Interceptor` supprimé retirait une défense en profondeur.** Condition du lot remplie
   (matrice + suite complète vertes sans lui), mais un test vert n'est pas une preuve de
   couverture. Le remettre est un changement d'une ligne dans `application.yml`. **À faire
   valider.**
3. **⚠ La clé `JWT_SECRET` historique est dans l'historique git** et doit être considérée
   compromise : rotation obligatoire avant production, action humaine hors de mon périmètre.
4. **Références imbriquées non vérifiées** : `Article.category`, `CustomerOrder.customerId`,
   `PurchaseOrder.supplierId`, l'`article` des lignes — un id d'un autre tenant passe encore.
5. **Les utilisateurs non-admin existants perdent l'accès en écriture** tant qu'aucun rôle ne leur
   est attribué (conséquence voulue de 5c, mais rupture fonctionnelle à planifier).
6. **Pas de permission sur les 3 endpoints `/{ressource}/{id}/photo`** : appartenance seule.
7. **Cycle `identity ↔ tenant` contourné, pas résolu.**
8. **`legacy/` existe encore** (20 classes) : supprimable seulement après migration des données
   historiques.

## 7. Note de transparence sur `CLAUDE.md`

`CLAUDE.md` vit **un niveau au-dessus de la racine git** (`gestion_de_stock/CLAUDE.md`) et n'est
donc **pas suivi par le dépôt**. Je l'ai mis à jour à chaque incrément comme demandé (il fait 68
lignes), mais ces mises à jour **n'apparaissent dans aucun commit** et sont invisibles pour qui
relit l'historique ou clone le projet. Déjà signalé dans le rapport L0, répété ici : le déplacer
dans le dépôt est une décision à prendre.
