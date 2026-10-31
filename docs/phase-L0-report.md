# Incrément L0 — Chaîne d'outils : Java 21 + Lombok — Rapport

Premier incrément du Lot 1. Objectif : rendre le build reproductible sur la chaîne d'outils
réellement installée, et faire échouer explicitement toute chaîne non supportée.

## 1. Investigation

| Constat | Vérifié par |
|---|---|
| `pom.xml` déclarait `java.version=17` alors que la cible documentée est 21 LTS | lecture `pom.xml` |
| `lombok.version=1.18.30` | lecture `pom.xml` |
| Le JDK de l'environnement est passé à **25.0.2** pendant la Phase 5b-2e (`JAVA_HOME=~/.jdks/openjdk-25.0.2`) | `java -version`, `JAVA_HOME` |
| Lombok 1.18.30 casse sous JDK 25 : `ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN` | reproduit en 5b-2e, re-constaté avant correctif |
| Le wrapper Maven était en **3.6.3** | `.mvn/wrapper/maven-wrapper.properties` |
| JDK 21 encore présent (`~/.jdks/ms-21.0.10`, `ms-21.0.9`) | `ls ~/.jdks` |

## 2. Décisions, et alternatives écartées

**Lombok 1.18.46** (depuis 1.18.30). Retenue parce qu'elle est la plus récente déjà présente dans
le cache `~/.m2` (donc n'introduit aucune dépendance au réseau pour un build futur) et qu'elle
couvre à la fois JDK 21 et JDK 25 — les deux ont été **vérifiés par un build complet**, pas
supposés. Alternative écartée : s'arrêter à 1.18.34 (le minimum demandé par le prompt), qui couvre
JDK 23 mais dont le support de JDK 25 n'est pas garanti — or l'environnement est en 25.

**Wrapper Maven 3.9.12** (depuis 3.6.3). Le prompt demande d'exiger Maven ≥ 3.9 ; l'exiger sans
monter le wrapper aurait cassé le build immédiatement (contradiction prompt/code, traitée selon la
règle « le code fait foi » : j'adapte et je signale). 3.9.12 est choisie parce qu'elle est déjà dans
`~/.m2/wrapper/dists` (3.9.16 l'est aussi ; 3.9.12 suffit et reste conservatrice).

**`maven-enforcer-plugin` 3.6.1** (version déjà en cache), exécution liée à la phase par défaut,
avec `requireJavaVersion [21,)` et `requireMavenVersion [3.9,)` et un message en clair pour chacune.
Objectif explicite : l'écart rencontré en 5b-2e produisait une erreur interne de `javac`
incompréhensible ; il produit désormais un message d'outillage sans ambiguïté.

**Aucune réécriture de syntaxe pré-21.** Le prompt l'autorise explicitement (« tu peux t'en tenir à
ne rien réécrire si le gain est nul »). Décision : ne rien réécrire. Motifs : le gain est nul, le
risque est non nul, et le critère de sortie exige un diff de production limité à `pom.xml`. Un
refactor de masse `record`/`switch` aurait mélangé un changement d'outillage avec des changements
de code — exactement ce que la règle de périmètre interdit.

## 3. Rouges observés

Cet incrément n'est pas la correction d'une faille : il n'y a pas de test de sécurité rouge→vert à
produire. Le « rouge » pertinent est celui de la chaîne d'outils, et il était **déjà reproduit**
avant correctif (phase 5b-2e, re-constaté ici) :

```
[ERROR] Failed to execute goal ...:compile (default-compile) on project gestiondestock:
        Fatal error compiling: java.lang.ExceptionInInitializerError:
        com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

Après correctif, `grep TypeTag` sur le log du build JDK 25 : **0 occurrence**.

## 4. Vérification

| Chaîne | Résultat |
|---|---|
| JDK 21.0.10 + Maven 3.9.12 — `./mvnw clean verify` | **vert**, 244 tests, 0 échec, 0 erreur |
| JDK 25.0.2 + Maven 3.9.12 — `./mvnw clean verify` | **vert**, 244 tests, 0 échec, 0 erreur |

Le projet compile et passe donc sur les deux JDK, ce qui lève la contrainte
`JAVA_HOME=~/.jdks/ms-21.0.10` imposée depuis 5b-2e.

## 5. Critères de sortie

| Critère | Statut |
|---|---|
| `./mvnw clean verify` vert | **Vérifié** (JDK 21 et JDK 25) |
| Build vert sous JDK 21 | **Vérifié** |
| Build vert sous JDK 25 | **Vérifié** (le prompt ne l'exigeait que « si disponible ») |
| Aucun changement fonctionnel | **Vérifié** — diff de production = `pom.xml` seul, aucun fichier `src/main` touché |
| Diff limité à `pom.xml` (+ imports éventuels) | **Vérifié** — aucun import modifié, aucune réécriture de syntaxe |
| `java.version` = 21 | **Vérifié** |
| Enforcer JDK ≥ 21 et Maven ≥ 3.9 | **Vérifié** |
| Nombre de tests ≥ précédent (244) | **Vérifié** — 244, inchangé (incrément d'outillage, aucun test ajouté) |

## 6. Fichiers modifiés

- `pom.xml` — `java.version` 17→21, `lombok.version` 1.18.30→1.18.46, ajout de
  `maven-enforcer-plugin`.
- `.mvn/wrapper/maven-wrapper.properties` — Maven 3.6.3→3.9.12.
- `docs/phase-L0-report.md`, `docs/migration-notes.md`.
- *(hors dépôt, voir §7)* `CLAUDE.md` — section Conventions passée à Java 21 + règles d'outillage.

## 7. Limites assumées et écarts signalés

- **`CLAUDE.md` n'est pas sous contrôle de version dans ce dépôt.** Il se trouve à
  `../CLAUDE.md`, un niveau **au-dessus** de la racine git (`git ls-files` : non suivi ;
  `git ls-files --error-unmatch ../CLAUDE.md` : « hors du dépôt »). Le prompt et le contexte le
  décrivent comme « checked into the codebase » — ce n'est pas le cas ici. Conséquence concrète :
  **un auditeur relisant les commits ne verra aucune de ses mises à jour.** Le fichier a tout de
  même été mis à jour, puisqu'il pilote le comportement des sessions suivantes, mais cette mise à
  jour n'est pas auditable par le dépôt. À trancher par le propriétaire : déplacer `CLAUDE.md` dans
  le dépôt, ou accepter qu'il reste un artefact local.
- **Enforcer non testé en échec.** Je n'ai pas exécuté de build sur un JDK < 21 ni un Maven < 3.9
  pour voir le message d'erreur réellement s'afficher : aucun JDK < 21 n'est configuré en
  `JAVA_HOME` et rétrograder le wrapper pour le prouver aurait été un aller-retour sans valeur.
  La règle est donc **déclarée mais non vérifiée en conditions d'échec** — c'est une limite réelle,
  pas une omission.
- **Pas de banc d'essai sur JDK 17.** Le projet n'est plus garanti compatible 17 (c'était l'objet de
  l'incrément) ; aucune vérification n'a été faite dans ce sens, et l'enforcer l'interdit désormais.

## 8. Backlog découvert

- `maven-compiler-plugin` est épinglé en **3.8.1**, une version ancienne (gérée hors du BOM
  Spring Boot par une déclaration explicite). Elle fonctionne sous JDK 21 et 25, donc je n'y ai pas
  touché (hors périmètre), mais c'est un candidat à alignement sur la version du parent lors d'un
  futur incrément d'outillage.
- `maven-wrapper` lui-même reste en `io.takari:maven-wrapper:0.5.6`, implémentation historique
  remplacée depuis par le wrapper officiel Apache. Fonctionne, non touché.
