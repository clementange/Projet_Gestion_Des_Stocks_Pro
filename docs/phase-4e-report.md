# Phase 4e — Suppression du legacy plat — Rapport

Objectif : vider les cinq paquets plats `model/`, `dto/`, `repository/`, `services/`,
`controller/`. **Atteint — les cinq paquets n'existent plus**, et une règle ArchUnit interdit de
les recréer. La carte classe-par-classe est dans `docs/api-migration-map.md` ; ce rapport couvre la
méthode, les décisions et les écarts.

## 1. Avant / après

| Paquet | Avant 4e | Après 4e |
|---|---|---|
| `model/` | 15 | **supprimé** |
| `dto/` | 12 | **supprimé** |
| `repository/` | 5 | **supprimé** |
| `services/` | 3 | **supprimé** |
| `controller/` | 4 | **supprimé** |
| `interceptor/` | 1 | **supprimé** (voir §5) |
| `shared/` (nouveau) | — | 5 |
| `legacy/` (nouveau) | — | 20 |
| `config/` | 4 | 5 (+`ApplicationUserDetailsService`) |
| `validator/` | 3 | 2 (−`UtilisateurValidator`) |
| **Total `src/main/java`** | 248 | **234** |

Soit **40 classes retirées des paquets plats** : 10 supprimées purement et simplement, 4 vers
`shared`, 5 vers `identity`, 1 vers `config`, 18 vers `legacy`, 2 absorbées (`UtilisateurApi`/
`AuthenticationApi` fusionnées dans leurs contrôleurs respectifs côté structure de paquet).

## 2. Méthode

Classement à partir des **importateurs réels** (`import com.kfokam48.gestiondestock.<paquet>.<X>;`)
et non d'une lecture de nom, puis des **mappings JPA** pour les entités. Ce second passage a
corrigé le premier : voir §4.

Exécution en quatre sous-lots, chacun vert (`./mvnw clean verify`), commité et poussé avant le
suivant :

| Sous-lot | Contenu | Tests |
|---|---|---|
| **4e-a** | briques transverses → `shared` | 272 verts |
| **4e-b** | authentification → `identity` + `config` | 272 verts |
| **4e-c** | suppression du contrat `/utilisateurs/**` | 270 verts |
| **4e-d** | cluster mort supprimé + historique → `legacy` + règle ArchUnit | 271 verts |
| **4e-e** | `Interceptor`, documentation | voir §5 |

## 3. Deux cycles que les paquets plats masquaient

C'est le résultat le plus important de l'incrément, et il n'était pas prévu. Les paquets plats
étaient **exclus du graphe de modules** (`ModuleBoundaries.LEGACY_FLAT_PACKAGES`) ; y laisser une
classe suffisait donc à cacher une dépendance circulaire. En les vidant, deux cycles réels sont
apparus immédiatement — `ModularityTests` et `ArchitectureRulesTest` les ont rejetés au premier
run.

### `identity ↔ tenant` (4e-b)

`ApplicationUserDetailsService` compose `identity` (`UserService`, `UserRoleAssignmentService`)
**et** `tenant` (`TenantService`, pour résoudre `organizationId` depuis `Tenant.idEntreprise`). Or
`tenant` dépend déjà de `identity` : `TenantRegistrationServiceImpl` appelle `UserService` pour
créer l'admin d'amorçage.

**Décision** : la classe va dans `config/`, pas dans `identity`. C'est un adaptateur
d'authentification Spring Security qui n'appartient à aucun des deux modules, et sa place est aux
côtés de `SecurityConfiguration` et `ApplicationRequestFilter`, qui l'utilisent tous deux.
**Alternative écartée** : dupliquer dans `identity` l'accès à `Tenant.organizationId`, ce qui
aurait recréé exactement la duplication que la migration élimine. Résoudre le cycle
`identity ↔ tenant` lui-même est une décision d'architecture distincte, **hors périmètre** et mise
en backlog.

### `catalog ↔ legacy` (4e-d)

`ArticleServiceImpl.delete` refusait de supprimer un article référencé par une ligne historique, en
lisant les trois repositories legacy. Mais `legacy` dépend de `catalog` : ses entités portent un
`@ManyToOne Article` et ses DTO exposent un `ArticleDto`.

**Décision : inversion de dépendance.** `catalog` déclare
`catalog.domain.port.ArticleUsagePort` (`Optional<String> findBlockingHistoricalUsage(Long)`),
`legacy.LegacyArticleUsageAdapter` l'implémente. `catalog` ne connaît plus `legacy`.

Deux détails qui comptent : les trois messages d'erreur HTTP sont **inchangés au caractère** (le
port renvoie le libellé « commandes client » / « commandes fournisseur » / « ventes » que le
service concatène comme avant), et l'**ordre de vérification est conservé** car il décide du
message renvoyé quand un article apparaît dans plusieurs historiques.

`shared` et `legacy` sont de **vrais modules**, volontairement absents de la liste d'exclusion :
c'est précisément ce qui permet à ces règles de mordre.

## 4. Correction du plan par le code

La première version de `api-migration-map.md` annonçait la suppression de neuf entités legacy
(`Client`, `Fournisseur`, `Ventes`, `CommandeClient`, `CommandeFournisseur`, …) sur la foi des
seuls importateurs. **Faux.** La lecture des mappings JPA montre que les trois entités de ligne
portent un `@ManyToOne` vers leur parent (`LigneVente → Ventes`,
`LigneCommandeClient → CommandeClient → Client` + `EtatCommande`,
`LigneCommandeFournisseur → CommandeFournisseur → Fournisseur`) et que `LigneVenteDto` **expose
`vente` dans son JSON**. Tout le cluster est une seule pièce.

Ces classes portent l'historique **antérieur à la migration**, que les tables des modules ne
contiennent pas et que `/articles/historique/**` fusionne avec elles. Les supprimer aurait retiré
des données de la réponse HTTP : une régression fonctionnelle, pas un nettoyage. Elles sont donc
**déplacées** dans un module `legacy` explicite. L'objectif « vider les paquets plats » est atteint ;
l'objectif implicite « faire disparaître le legacy » ne l'est pas, et ne peut pas l'être sans une
migration de données. La carte a été corrigée en conséquence.

Ne restaient réellement morts que **cinq doublons de tables déjà servies par un module** :
`Entreprise` + `EntrepriseRepository` (table `entreprise`, servie par `tenant.Tenant`),
`Utilisateur` + `UtilisateurRepository` et `Roles` (tables `utilisateur`/`roles`, servies par
`identity`) — ces trois derniers n'étant devenus morts qu'après 4e-c.

## 5. Interceptor — supprimé, condition vérifiée

`interceptor/Interceptor` (réécriture SQL ajoutant `and <entite>.identreprise = <MDC>` aux
`select`) est listé par `CLAUDE.md` comme zone sensible. La consigne de lot autorisait sa
suppression **si** `CrossTenantEndpointMatrixTest` restait vert sans lui.

Protocole suivi — j'ai désactivé le `statement_inspector` dans `application.yml` puis mesuré :

| Mesure, intercepteur désactivé | Résultat |
|---|---|
| `CrossTenantEndpointMatrixTest` (71 sondes + 11 contrôles d'effet de bord) | **vert** |
| Suite complète `./mvnw clean verify` | **vert, 271 tests** |

Condition remplie sur les deux niveaux, donc supprimé. L'isolation multi-tenant repose désormais
**entièrement** sur les vérifications d'appartenance explicites des Phases 5b-2a à 5b-2h.

**⚠ Risque résiduel à faire valider.** Un test vert n'est pas une preuve de couverture totale :
l'allowlist de l'intercepteur couvrait aussi `mvtstk` et `ventes`, dont toutes les requêtes de
lecture ne sont pas individuellement sondées par la matrice. Ce qui disparaît est une **défense en
profondeur** : désormais, une lecture qui oublie sa vérification d'appartenance fuit sans filet.
Le remettre est un changement d'une ligne dans `application.yml`. `CLAUDE.md` a été mis à jour pour
dire explicitement qu'il n'y a plus de filet SQL.

`MDC.put("idEntreprise", …)` dans `ApplicationRequestFilter` est **conservé** : l'intercepteur en
était le seul consommateur, mais la valeur reste utile comme contexte de log (corrélation par
tenant). Un commentaire le dit, pour que la ligne ne paraisse pas morte.

## 6. Contrat HTTP `/utilisateurs/**` — supprimé (4e-c)

`/users/**` est le contrat canonique depuis la Phase 4a et couvre chaque route supprimée ; les
rapports 5b-2h et 5c annonçaient déjà cette suppression en 4e. Elle **ferme au passage les deux
derniers trous de permission du dépôt** : `/utilisateurs/create` et
`DELETE /utilisateurs/delete/{id}` n'avaient que l'appartenance, jamais la permission (listés en
exclusion justifiée dans `WriteEndpointPermissionGateTest`, maintenant retirés).

**Différences de contrat** (tableau complet dans la carte §3) : `moteDePasse` → `motDePasse`,
`entreprise:{id}` → `idEntreprise`, codes `UTILISATEUR_*` → `USER_*`, et le champ `roles` n'est
plus renvoyé (le RBAC passe par `/user-role-assignments/**`).

## 7. Tests existants modifiés

| Test | Modification | Justification |
|---|---|---|
| `legacy/UtilisateurAuthenticationCharacterizationTest` → `identity/AuthenticationCharacterizationTest` | 13 cas → 11, chacun **porté** sur `/users/**` | le contrat que ce filet protégeait n'existe plus. Les assertions sont identiques ; seuls URL, nom de champ et préfixe de code d'erreur changent. Supprimer le fichier aurait perdu 11 vérifications de comportement réelles |
| idem, cas `createDoesNotRequireAnySpecificPermissionForAnyAuthenticatedUser` | **non porté, supprimé** | il verrouillait délibérément un BUG (aucune permission sur la création d'utilisateur). La Phase 5c l'a corrigé et `WritePermissionEnforcementHttpIntegrationTest` prouve le comportement inverse. Le porter aurait consisté à ré-affirmer une faille fermée |
| idem, cas `changerMotDePasseRejectsWhenCallerIsNotTargetUser` | fusionné avec son équivalent `/users/**` | les deux cas étaient identiques une fois la route legacy supprimée |
| 4 tests utilisant `/utilisateurs/create` comme fixture | basculés sur `/users/create` | adaptation mécanique. **Un échec réel en est sorti** : `BusinessModulesHttpIntegrationTest` envoyait encore `entreprise:{id}`, ignoré par `UserDto`, donc l'utilisateur n'avait plus de tenant → 404 au lieu de 400. Corrigé en `idEntreprise` |
| `CrossTenantEndpointMatrixTest`, `CreateEndpointIdGuardTest`, `WriteEndpointPermissionGateTest` | 3 sondes et 7 entrées d'inventaire retirées | les routes n'existent plus ; ces tests échouent par construction si l'inventaire dérive |
| `legacy/ArticleHistoryLegacyEndpointsTest` | `EntrepriseRepository` → `TenantService` | le doublon legacy de la table `entreprise` est supprimé ; `tenant` en est le seul propriétaire |
| `ModuleBoundaries` | liste d'exclusion réduite à 5 paquets techniques | les paquets plats n'existent plus ; `shared` et `legacy` doivent être vérifiés comme les autres modules |

## 8. La nouvelle règle, vérifiée rouge

`ArchitectureRulesTest.the_flat_legacy_packages_must_never_come_back` interdit toute classe dans
les cinq paquets. Une règle de ce genre passe trivialement sur un ensemble vide : je l'ai donc
**vérifiée rouge avant de la déclarer verte**, en créant une classe sonde dans `model/` —

```
Architecture Violation [Priority: MEDIUM] - Rule 'no classes should reside in any package
['com.kfokam48.gestiondestock.model..', …]' was violated (1 times)
```

— puis en la supprimant. La règle mord sur une seule classe réintroduite.

## 9. Critères de sortie

| Critère | Statut |
|---|---|
| `model/`, `dto/`, `repository/`, `services/`, `controller/` vidés | **Vérifié** — les cinq répertoires n'existent plus |
| Tableau d'investigation « classe → destination ou suppression » | **Vérifié** — `docs/api-migration-map.md`, 43 classes |
| Exécution en sous-lots, vert + commit + push après chacun | **Vérifié** — 4e-a à 4e-d |
| Règle ArchUnit interdisant les paquets plats | **Vérifié**, et vérifiée rouge (§8) |
| `Interceptor` : supprimé seulement si la matrice reste verte | **Vérifié** — matrice + suite complète vertes sans lui (§5) |
| `docs/api-migration-map.md` | **Vérifié** |
| `docs/ARCHITECTURE.md` et `CLAUDE.md` mis à jour | **Vérifié** |
| Tableau avant/après du nombre de classes | **Vérifié** — §1 |
| Suite complète verte | **Vérifié** |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** — 7 règles (6 avant) |
| Aucun test désactivé, aucune assertion affaiblie | **Vérifié** — voir §7 |

## 10. Limites assumées

- **`legacy/` existe toujours, avec 20 classes.** L'objectif « vider les paquets plats » est
  atteint, pas « supprimer le legacy » : les données historiques l'interdisent sans migration de
  données (§4). Le module est nommé, documenté et sous règle de non-croissance, mais c'est une
  dette renommée, pas effacée.
- **`LigneCommandeFournisseurDto` expose une entité JPA** (`CommandeFournisseur`) au lieu d'un DTO.
  Défaut préexistant ; le corriger changerait la forme JSON d'un contrat externe. Non touché,
  signalé.
- **Le cycle `identity ↔ tenant` n'est pas résolu**, seulement contourné en sortant la classe des
  deux modules (§3).
- **Un commit intermédiaire ne compile pas.** `git add -A` s'est interrompu sur un fichier
  temporaire pendant 4e-a : le commit `0276452` ne contient que les 4 renommages, sans les 79
  fichiers d'imports. Corrigé par `ebd0196` dans la minute, et la branche n'est jamais restée
  rouge publiée plus longtemps — mais `0276452` seul ne compile pas, et je le signale plutôt que de
  réécrire l'historique.
- **La suppression de l'`Interceptor` retire une défense en profondeur** sur la base d'une
  couverture de test, pas d'une preuve (§5). À faire valider.

## 11. Backlog découvert

- **Résoudre le cycle `identity ↔ tenant`** : extraire la résolution `idEntreprise → organizationId`
  dans un port déclaré par `identity`, sur le modèle de `ArticleUsagePort`.
- **`LigneCommandeFournisseurDto` → vrai DTO** pour la référence `commandeFournisseur` (§10).
- **Migrer les données historiques** des trois tables plates vers les tables des modules, seule
  voie pour supprimer `legacy/` pour de bon.
- **`/articles/historique/**` renvoie deux formes JSON différentes** selon que la ligne vient du
  legacy ou d'un module (DTO legacy vs DTO de module fusionnés dans la même liste). Non vérifié en
  détail ici, à confirmer comme contrat voulu.
