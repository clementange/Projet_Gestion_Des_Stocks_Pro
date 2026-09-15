# Phase 3c — module `identity` — Rapport de sortie d'incrément

Troisième module traité pour la séparation domaine/persistance
(`Permission`, `Role`, `UserRoleAssignment`), dernier des trois modules
"faciles" (sans invariant métier) avant `inventory`. Même règle : un seul
module par incrément, arrêt et rapport avant le suivant.

## 1. Vérifications préalables (avant d'écrire une ligne de code)

Le contexte fourni par l'utilisateur (zéro consommateur externe sur les 3
entités, colonnes physiques déjà identiques aux annotations, absence
d'invariant métier) a été revérifié indépendamment plutôt que pris pour
acquis sans contrôle, par principe — comme pour `organization`/`catalog` :

- **Consommateurs externes** : recherche exhaustive de
  `identity.domain.model.{Permission,Role,UserRoleAssignment}` en dehors
  de `identity/` — **0 résultat pour les trois**. Confirmé.
- **Colonnes physiques** — vérifiées dans `V1__initial_schema.sql` :
  `permission` (`code` NOT NULL, `description` nullable),
  `role` (`code`/`name` NOT NULL, `description` nullable),
  `role_permission` (table de jointure pure, PK composite
  `(role_id, permission_id)`, pas de colonne `id`),
  `user_role_assignment` (`user_id`/`role_id`/`scope_type` NOT NULL,
  `scope_id` nullable, contrainte `CHECK` sur les valeurs de
  `scope_type`). Toutes identiques aux noms de champs actuels — **aucun
  écart type `tax_code` trouvé**, confirmé comme annoncé.
- **Invariants métier** : confirmé nul sur les trois classes (`@Data`
  purs).

## 2. Étapes : `Permission` → `Role` → `UserRoleAssignment`

Ordre proposé par l'utilisateur suivi tel quel (Permission d'abord pour le
cas le plus simple, Role ensuite pour exercer `@ManyToMany`/`@JoinTable`
avant que `UserRoleAssignment` n'en dépende via `@ManyToOne`) — aucune
raison technique identifiée de dévier de cet ordre.

Toutes les entités ajoutées au même `META-INF/orm.xml` partagé (pas de
nouveau fichier), avec `class` pleinement qualifié comme pour `catalog`
(le fichier reste scopé par défaut sur `organization.domain.model`).

### Étape 1 — `Permission` (spike)

Entité la plus simple des trois (deux colonnes scalaires, aucune
relation). Annotations retirées, `<basic>` + `<column unique="true">` pour
`code`. Testé avec `AuthorizationServiceIntegrationTest` (exerce
`permissionService.save`/`findAll` réellement). **Succès sans surprise.**

### Étape 2 — `Role` (nouveauté : `@ManyToMany`/`@JoinTable`)

Première relation many-to-many de tout `orm.xml` jusqu'ici (les modules
précédents n'avaient que `@ManyToOne`/`@OneToOne`/`@OneToMany`). Traduite
en `<many-to-many><join-table name="role_permission"><join-column
name="role_id"/><inverse-join-column name="permission_id"/></join-table></many-to-many>`.
Testé avec `RbacAdminEndpointsHttpIntegrationTest` (3 tests) et surtout
`AuthorizationServiceIntegrationTest`, dont la lecture du code confirme
qu'il construit des `RoleDto` avec un **jeu de permissions non vide**
(`.permissions(Set.of(...))`), donc exerce réellement des lignes dans
`role_permission`, pas seulement un ensemble vide qui aurait masqué une
erreur de mapping. **Succès sans surprise, sans ajustement.**

### Étape 3 — `UserRoleAssignment`

`@ManyToOne` vers `Role` (déjà connu du pattern `organization`/`catalog`)
+ `@Enumerated(STRING)` sur `scopeType` (déjà connu du pattern
`organization.Site.type`) + un `Long userId` simple (pas une relation JPA,
confirmé — `<basic>` suffit, comme anticipé). Rien de nouveau
techniquement. **Succès sans surprise.**

## 3. Critère de sortie — vérifié point par point

| Critère | Résultat |
|---|---|
| Violations JPA disparues pour les 3 entités identity uniquement | **Vérifié** — 0 occurrence de `identity.domain.model.{Permission,Role,UserRoleAssignment}` dans le rapport de violation post-fix. Total passé de 196 à **169** (27 violations). |
| Les 12 agrégats restants inchangés | **Vérifié** — 169 violations restantes, aucune sur `catalog`/`organization`/`identity` (tous à 0), le reste inchangé. |
| Tous les tests existants verts sans modification d'assertion | **Vérifié** — `./mvnw clean verify` : 129 tests, 1 seul échec (la règle JPA elle-même, attendue), 0 régression, 0 fichier de test modifié. |
| Zéro fichier touché hors `identity/` et `META-INF/orm.xml` | **Vérifié** — `git status --short` hors `identity/`/`META-INF/` : vide (aucun consommateur externe à vérifier individuellement, puisqu'il n'y en a aucun — confirmé en §1). |

## 4. Fichiers modifiés

**Production** :
- `identity/domain/model/Permission.java`
- `identity/domain/model/Role.java`
- `identity/domain/model/UserRoleAssignment.java`
- `src/main/resources/META-INF/orm.xml` (fichier existant, complété)

**Aucun fichier de test modifié ou ajouté** —
`AuthorizationServiceIntegrationTest` (2 tests) et
`RbacAdminEndpointsHttpIntegrationTest` (3 tests), déjà existants,
constituent le test de non-régression, avec une vérification a posteriori
que le second exerce bien des lignes non vides dans la table de jointure.

**Docs** :
- `docs/migration-notes.md` — 9/21 entités résolues (était 6/21), 12
  restantes, la nouveauté `@ManyToMany`/`@JoinTable` actée comme résolue
  sans réserve pour la suite.

## 5. Ce qui a été confirmé pour la suite

Troisième module consécutif sans invariant métier, troisième succès sans
itération de correction. Le pattern `orm.xml` couvre maintenant tous les
types de relations JPA rencontrés dans ce projet à l'exception de celles
propres à `inventory`/modules avec invariants (aucune relation
`@OneToOne`/`@Version` combinée à une logique métier testée pour l'instant
— `Warehouse.site` était un `@OneToOne` simple, sans invariant).

La question laissée ouverte à la fin du rapport `catalog` reste entière :
ni `organization`, ni `catalog`, ni `identity` n'avaient de méthode de
domaine sur leurs entités. `inventory.Stock`/`StockMovement` (et
`PurchaseOrder`, `CustomerOrder`, etc. dans les modules suivants) en ont
réellement — la séparation domaine/persistance devra alors prouver qu'elle
préserve ce comportement, pas seulement des accesseurs de champs. Ce
n'est toujours pas éclairé par les trois premiers incréments.

## 6. Zones d'ombre / décisions prises en cours de route

Aucune. Incrément sans ambiguïté, conforme point par point au contexte
déjà vérifié par l'utilisateur.

## 7. Prochaine étape

Module `identity` terminé et vérifié vert sur son périmètre. **N'enchaîne
pas sur `inventory` sans feu vert explicite** — l'approche pour ce module
(Stock/StockMovement avec invariants métier réels) reste à discuter avant
d'écrire quoi que ce soit, plutôt que de présumer qu'`orm.xml` seul
suffit.
