# Phase P1 — Migration des tests JUnit 4 → JUnit 5 (Jupiter) — Rapport

Préalable à P2 : Spring Framework 7 / Boot 4 abandonnent JUnit 4 et `SpringRunner`. Migrer
maintenant, sur un framework encore connu, isole les risques.

## 1. Inventaire de départ

| Construction | Fichiers |
|---|---|
| `@RunWith(SpringRunner.class)` | 39 |
| `import org.junit.Test` | 40 |
| `import org.junit.Before` | 4 |
| `org.junit.Assert.*` qualifié | 36 appels |
| `import static org.junit.Assert.*` | 23 imports |
| `@Test(expected = …)` | 4 |
| `@Rule`, `@Ignore`, `ExpectedException`, `Parameterized`, `@After`, `@Test(timeout=…)` | **0** |

**Aucun `@Ignore`** : rien à arbitrer de ce côté, et rien n'a été désactivé pendant la migration.
19 fichiers mélangeaient déjà JUnit 4 et Jupiter (`assertThrows` de Jupiter, `@Test` de JUnit 4) —
un mélange que le moteur vintage rendait invisible.

**Baseline de décompte : 283 tests.**

## 2. Règle de transformation

Appliquée par script, puis vérifiée par le compilateur et par le décompte. La règle, et non chaque
ligne, est ce qui doit être relu :

| Avant | Après |
|---|---|
| `@RunWith(SpringRunner.class)` | **supprimé** — `@SpringBootTest` suffit, il embarque déjà `SpringExtension` |
| `org.junit.Test` | `org.junit.jupiter.api.Test` |
| `@Before` / `org.junit.Before` | `@BeforeEach` / `org.junit.jupiter.api.BeforeEach` |
| `org.junit.Assert` (importé ou qualifié) | `org.junit.jupiter.api.Assertions` |
| `@Test(expected = X.class)` | `assertThrows(X.class, () -> …)` |

### Le point qui demandait de la prudence : l'ordre des arguments

En JUnit 4 le message est le **premier** argument, en JUnit 5 le **dernier**. Un renommage seul
aurait produit du code qui **compile dans certains cas et change de sens** :
`assertEquals("Client Renomme", updated.getNom())` est un couple attendu/réel, **pas** un message —
le déplacer aurait inversé l'assertion sans que rien ne proteste.

Le réordonnancement est donc conditionné à l'**arité exacte**, jamais à la seule présence d'une
chaîne en tête :

- `assertEquals` / `assertNotEquals` / `assertSame` / `assertArrayEquals` : **3** arguments **et**
  premier littéral chaîne → le message passe en dernier. (Un `assertEquals(double, double, delta)`
  a 3 arguments mais pas de chaîne en tête : non touché.)
- `assertTrue` / `assertFalse` / `assertNull` / `assertNotNull` : **2** arguments **et** premier
  littéral chaîne → idem.

**23 assertions réordonnées** automatiquement, **40 fichiers** modifiés.

## 3. Ce que le script n'a volontairement pas fait, et que le compilateur a rattrapé

Deux limites assumées de la condition « premier argument littéral chaîne », qui ont laissé
**4 appels** non convertis — tous signalés par `javac`, aucun passé silencieusement :

| Cas | Pourquoi le script s'est abstenu |
|---|---|
| `assertTrue(serviceName + " est declare protege…", callsGuard)` ×2 | le message **commence par une variable**, pas par un littéral : impossible de distinguer mécaniquement un message d'un attendu |
| `org.junit.Assert.assertNotEquals(…)` qualifié ×2 | le lookbehind `(?<![\w.])` excluait délibérément les appels **pleinement qualifiés**, pour ne pas toucher à un `Assertions` d'une autre bibliothèque |

C'est le comportement voulu : un transformateur qui doute s'abstient, et l'erreur de compilation
est un signal fiable. Les 4 ont été corrigés à la main.

**`@Test(expected = …)`** : mon premier script tentait de les réécrire par regex en enveloppant le
corps de la méthode. Il a planté sur un corps dont l'accolade fermante ne s'alignait pas comme
prévu, **après avoir déjà modifié des fichiers**. J'ai remis `src/test` à l'état du commit, désactivé
cette partie et traité les 4 cas à la main. `assertThrows` est d'ailleurs meilleur que
`@Test(expected=…)` : il cible l'instruction attendue en échec, au lieu d'accepter l'exception
depuis n'importe quel point de la méthode.

## 4. `pom.xml`

`junit:junit` et `org.junit.vintage:junit-vintage-engine` sont **retirés**. Les laisser aurait
permis à une classe JUnit 4 de revenir sans que rien ne le signale — c'est précisément ce qui a
produit les 19 fichiers mixtes.

`spring-boot-starter-test` fournit Jupiter ; `archunit-junit5` et `testcontainers:junit-jupiter`
étaient déjà sur Jupiter.

## 5. Testcontainers

`AbstractIntegrationTest` utilise le pattern **conteneur singleton** (champs `static` + bloc
`static` qui démarre PostgreSQL et MinIO une fois pour toute la JVM de test), avec
`@DynamicPropertySource`. Ce pattern est **indépendant du moteur JUnit** : il n'utilise ni
`@Testcontainers` ni `@Container` (choix d'origine documenté dans la classe, précisément pour
rester compatible des deux côtés). Aucune modification n'a été nécessaire, et le décompte
identique montre qu'aucune classe n'a perdu son contexte.

Fuite entre classes : le conteneur est **partagé par conception** et les tests s'isolent par des
données uniques (`uniq(...)`, tenants créés par test), pas par une base vierge. C'est l'état
antérieur, inchangé par P1 — voir §7.

## 6. Critères de sortie

| Critère | Statut |
|---|---|
| 0 `import org.junit.` hors `org.junit.jupiter` | **Vérifié** — grep vide |
| Aucune dépendance JUnit 4 ni moteur vintage | **Vérifié** — `pom.xml` |
| Nombre de tests identique (283 → 283) | **Vérifié** — décompte surefire, aucun écart |
| Suite complète verte | **Vérifié** — 283 tests, 0 échec, 0 erreur |
| `ArchitectureRulesTest`, `ModularityTests`, matrice, gate RBAC, create-guard, références étrangères | **Vérifié** — inclus dans la suite |
| Aucun `@Disabled`, aucune assertion affaiblie | **Vérifié** — 0 `@Ignore` au départ, 0 `@Disabled` à l'arrivée |
| Aucun changement de `src/main` | **Vérifié** — le diff ne touche que `src/test` et `pom.xml` |

## 7. Limites assumées et backlog

- **Le décompte identique prouve la découverte, pas l'équivalence sémantique.** 283 tests
  s'exécutent et passent, mais un réordonnancement d'arguments erroné sur un `assertEquals` à 2
  arguments aurait pu produire un test qui passe pour une mauvaise raison. La protection est la
  règle d'arité (§2), pas le décompte. Je n'ai pas relu les 283 assertions une par une.
- **Les tests partagent un conteneur et une base.** Isolation par données uniques, pas par
  transaction ni par schéma. État antérieur à P1, non traité ici ; un `@Transactional` sur les
  tests de service ou un `@DirtiesContext` ciblé serait plus robuste. **Backlog.**
- **Pas de passage aux fonctionnalités Jupiter** (`@Nested`, `@ParameterizedTest`,
  `@DisplayName`) : hors périmètre, la migration est volontairement mécanique. Plusieurs tests
  d'inventaire gagneraient à devenir des `@ParameterizedTest`. **Backlog.**
- **`spring-security-test` et `spring-boot-starter-test`** restent aux versions gérées par le
  parent Boot 3.3.5 — P2 s'en occupe.
