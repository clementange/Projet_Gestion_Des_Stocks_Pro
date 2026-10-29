# Phase 5b-2e — suppression de POST /organizations/create — Rapport

Dernier item du backlog 5b-2 encore ouvert et explicitement gaté derrière confirmation
(`docs/phase-5b2c-report.md` §4, `docs/phase-5b2d-report.md` §7). Incrément délibérément minuscule :
un seul endpoint retiré, aucune nouvelle permission, aucune migration.

## 0. Investigation : l'endpoint n'avait plus aucun appelant

Vérifié par recherche exhaustive avant toute décision :

- **Production** : le seul créateur d'`Organization` est `TenantRegistrationServiceImpl` (ligne 72),
  qui appelle `OrganizationService.save()` **directement**, pas via HTTP — dans le cadre de
  l'orchestration complète d'inscription (Tenant + Organization miroir + Site par défaut + admin +
  affectation RBAC `ADMINISTRATEUR`/GLOBAL).
- **Tests** : zéro appel réel. Les deux seules occurrences de `organizations/create` dans
  `src/test` étaient des **commentaires** expliquant pourquoi les fixtures avaient *cessé* de
  l'utiliser, à la suite des correctifs de 5b-2b §3, 5b-2c §3 et 5b-2d §3.
- **Sécurité** : la route n'était pas dans la liste `permitAll` de `SecurityConfiguration` — elle
  était `authenticated()` par défaut, sans aucune autre vérification.

## 1. Pourquoi supprimer plutôt que gater

Une `Organization` créée par cette route n'avait ni `Tenant`, ni utilisateur admin, ni affectation
RBAC : une **orpheline inatteignable**, y compris par son propre créateur depuis que la lecture est
scopée (5b-2c l'a rendue self-only — l'appelant ne voit que l'organisation portée par son JWT, donc
jamais celle qu'il vient de créer). C'est exactement ce qui a cassé trois fixtures consécutives,
contournées au cas par cas à chaque fois.

L'option « gater derrière `RBAC_MANAGE` » a été explicitement écartée après analyse : elle **ne
corrigeait pas** le problème structurel, puisque tout admin de tenant possède déjà `RBAC_MANAGE` via
le bootstrap `ADMINISTRATEUR` (migration V4) — les organisations orphelines auraient continué d'être
créables par exactement les appelants qui les créaient déjà.

Précédent direct dans ce dépôt : **Phase 4b** a supprimé toute la couche
`EntrepriseService`/`Controller`/`Api`/`Dto`/`Validator` plutôt que d'en garder un adaptateur, avec
le même raisonnement textuel (« `/entreprises/create` sans l'orchestration RBAC/Organization
n'aurait plus eu aucune utilité fonctionnelle » — voir `docs/migration-notes.md`, section Phase 4b).
`/organizations/create` est le même cas, un cran plus loin dans la migration.

## 2. Conception

- `OrganizationController.save()` supprimé (handler + imports `PostMapping`/`RequestBody` devenus
  inutiles). Les trois autres routes (`findById`/`findAll`/`delete`, self-only depuis 5b-2c) sont
  **inchangées** — la suppression est ciblée sur la seule création.
- `OrganizationService.save()` **conservé intact** : toujours nécessaire à
  `TenantRegistrationServiceImpl`. Javadoc ajoutée sur la méthode d'interface pour documenter
  qu'elle n'a plus de surface HTTP et pourquoi il ne faut pas la réexposer sans l'orchestration
  complète.
- Javadoc de classe de `OrganizationController` mise à jour (l'ancienne annonçait encore « save()
  reste sans vérification — délibéré »), et le commentaire devenu faux dans
  `organizationDeleteWithDependentCityIsRejected` corrigé.

Aucun changement de contrat autre que le retrait de la route : aucun DTO touché, aucune signature de
service modifiée, aucune migration Flyway.

## 3. Tests

`OrganizationControllersHttpIntegrationTest.organizationCreateEndpointNoLongerExists` — `POST
/organizations/create` authentifié renvoie une erreur 4xx (route absente), et, dans le même test,
`GET /organizations/{id}` reste fonctionnel : preuve que la suppression est ciblée sur la création et
n'a pas cassé le reste du contrôleur.

Assertion volontairement `is4xxClientError()` plutôt que `isNotFound()`/`isMethodNotAllowed()` : le
code exact relève du routage Spring (`/organizations/create` collisionne partiellement avec le motif
`GET /organizations/{idOrganization}`), pas du contrat métier produit par `RestExceptionHandler`.
Pinner 404 ou 405 rendrait le test fragile face à une évolution de Spring sans rapport avec la
garantie testée. Les codes métier restent pinnés exactement partout ailleurs, comme dans tout le
reste de la suite.

Le chemin de création légitime reste couvert sans ajout : `adminToken()` (`POST /tenants/register`)
est exercé par chaque test de ce fichier, et
`EntrepriseServiceOrganizationMirrorIntegrationTest.creatingAnEntrepriseCreatesAMirrorOrganizationWithDefaultSite`
vérifie déjà explicitement la création de l'Organization miroir et de son site par défaut.

## 4. Écart d'environnement rencontré (hors périmètre, non corrigé)

En cours d'incrément, `JAVA_HOME` est passé de **JDK 21.0.12** (utilisé par tous les builds
antérieurs de la session) à **JDK 25.0.2**. Lombok 1.18.30 (version épinglée dans `pom.xml`) ne
supporte pas JDK 25 : tout `mvnw compile` échoue alors en
`ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` — erreur de chaîne
d'outils, sans aucun rapport avec le code de cet incrément (vérifié : le même code compile et passe
intégralement sous JDK 21).

Les builds de cet incrément ont donc tourné avec `JAVA_HOME=/home/.../.jdks/ms-21.0.10`, la cible
réelle du projet (CLAUDE.md : « Java 17, cible 21 LTS en cours de migration »). **Rien n'a été
modifié dans `pom.xml`** : monter Lombok pour supporter JDK 25 est un changement de dépendance
transverse, hors périmètre de cet incrément, et qui mérite sa propre validation. À traiter
séparément si l'environnement doit rester sur JDK 25.

## 5. Critère de sortie

| Critère | Résultat |
|---|---|
| `POST /organizations/create` n'est plus routable | **Vérifié** — test dédié. |
| Les autres routes d'`OrganizationController` restent opérationnelles | **Vérifié** — même test. |
| `OrganizationService.save()` intact et toujours fonctionnel pour l'inscription de tenant | **Vérifié** — `EntrepriseServiceOrganizationMirrorIntegrationTest` vert sans modification. |
| Aucune migration, aucun nouveau code de permission, aucun DTO touché | **Vérifié**. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 238 tests, 0 échec, 0 erreur (237 avant l'incrément, +1 test dédié). |

## 6. Fichiers modifiés

**Production** : `organization/presentation/rest/OrganizationController.java` (handler `save`
supprimé, javadoc de classe réécrite), `organization/application/OrganizationService.java` (javadoc
sur `save`, aucune signature changée).

**Test** : `organization/OrganizationControllersHttpIntegrationTest.java` (nouveau test + correction
d'un commentaire devenu faux).

**Docs** : `docs/migration-notes.md`.

## 7. Prochaine étape

5b-2 est désormais clos dans son intégralité : 5a, 5b-1, 5b-2a, 5b-2b, 5b-2c, 5b-2d, 5b-2e. Plus
aucune lecture ni frontière tenant connue n'est laissée ouverte sur les entités couvertes.

Backlog restant, jamais entamé et toujours gaté derrière feu vert explicite :

- **RBAC-on-create** — aucune vérification de permission sur les `create` de `Sale`,
  `CustomerOrder`, `PurchaseOrder`, `StockTransfer`, `Article`, `Category`, `Customer`, `Supplier`,
  `Site`, `Tenant` (catalogué depuis Phase 0 « Constats RBAC/permissions » et Phase 5b-1 §8). Les
  garde-fous d'appartenance posés en 5b-2b/2d ferment la frontière *tenant* sur ces chemins, mais
  pas la question « qui, dans l'organisation, a le droit de créer ». Incrément nettement plus gros,
  à découper.
- **Montée de Lombok pour JDK 25** — voir §4.

**N'enchaîne sur aucun de ces points sans feu vert explicite.**
