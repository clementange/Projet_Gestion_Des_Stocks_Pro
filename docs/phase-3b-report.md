# Phase 3b — Rapport de sortie d'incrément

Deuxième incrément de Phase 3 : éliminer les 16 violations d'injection par
champ trouvées en Phase 2 (`ArchitectureRulesTest.no_field_injection_anywhere`),
en remplaçant chaque champ `@Autowired`/`@Value` par un paramètre de
constructeur. Aucun autre changement de comportement — en particulier,
aucun correctif de sécurité déguisé sur `JwtUtil`/`ApplicationRequestFilter`
(ça reste le périmètre de la Phase 5).

## 1. Classes converties

| Classe | Champs avant | Après |
|---|---|---|
| `config.ApplicationRequestFilter` | `@Autowired JwtUtil jwtUtil`, `@Autowired ApplicationUserDetailsService userDetailsService` | constructeur `(JwtUtil, ApplicationUserDetailsService)`, champs `final` |
| `config.FlickrConfiguration` | 4 champs `@Value` (apiKey/apiSecret/appKey/appSecret) | constructeur a 4 parametres `@Value`, champs `final` |
| `controller.AuthenticationController` | `@Autowired AuthenticationManager`, `@Autowired ApplicationUserDetailsService`, `@Autowired JwtUtil` | constructeur a 3 parametres, champs `final` |
| `services.auth.ApplicationUserDetailsService` | `@Autowired UtilisateurService service`, `@Autowired UserRoleAssignmentService` | constructeur a 2 parametres, champs `final` |
| `services.impl.FlickrServiceImpl` | 4 champs `@Value` | constructeur a 4 parametres `@Value`, champs `final` ; import `Autowired` (deja inutilise avant ce commit) retire |
| `utils.JwtUtil` | 1 champ `@Value("${jwt.secret}")` | constructeur a 1 parametre `@Value`, champ `final` |

Aucune de ces 6 classes n'avait de constructeur explicite avant ce commit
(uniquement le constructeur implicite sans argument) : chacune en a reçu un
qui prend exactement les paramètres qui étaient injectés par champ, dans le
même ordre de déclaration que les champs d'origine. Noms de champs
inchangés (`service`, `userDetailsService`, etc.) pour minimiser le diff —
aucun renommage, même quand un nom plus explicite aurait été tentant
(hors périmètre de cet incrément).

## 2. Vérifications de sécurité effectuées (contraintes 2 et 3 de la consigne)

- **`JwtUtil`/`ApplicationRequestFilter`** : diff relu ligne à ligne après
  écriture — seul le mécanisme d'injection change. `@Value("${jwt.secret}")`
  reste résolu depuis la configuration externe (`application.yml` /
  variable d'environnement `JWT_SECRET`), aucune valeur en dur introduite,
  aucune logique de validation de token touchée.
- **Instanciation manuelle** : `grep -rn "new JwtUtil(\|new ApplicationRequestFilter(\|new AuthenticationController(\|new ApplicationUserDetailsService(\|new FlickrServiceImpl(\|new FlickrConfiguration("`
  sur tout `src/main` et `src/test` avant de commencer — **zéro résultat**.
  Aucun code n'instancie ces classes directement ; elles sont toutes
  gérées exclusivement par le conteneur Spring, donc un constructeur à
  arguments obligatoires ne casse rien.
- **Risque de dépendance circulaire** : l'injection par constructeur pure
  ne tolère pas les cycles entre beans (contrairement à l'injection par
  champ, que Spring peut résoudre via un proxy anticipé). Vérifié
  empiriquement en faisant démarrer le contexte Spring complet via
  `SecurityFilterChainIntegrationTest` (qui exerce la chaîne de filtres
  réelle, y compris un login complet emettant un vrai JWT) — 5/5 tests
  verts, aucune `BeanCurrentlyInCreationException`.

## 3. `config.FlickrConfiguration` — cas particulier

Cette classe porte `// @Configuration` **commenté** (déjà le cas avant ce
commit) : elle n'est donc pas un bean Spring actif aujourd'hui — code mort,
jamais instancié par le conteneur. La règle ArchUnit la détecte quand même
(analyse statique du bytecode, indépendante du fait qu'une classe soit
réellement enregistrée comme bean). Convertie de la même façon que les
autres pour rester cohérente et satisfaire la règle, sans que cela change
quoi que ce soit à son statut de code mort — elle reste aussi inerte
qu'avant.

## 4. Critère de sortie — vérifié

```
ArchitectureRulesTest.no_field_injection_anywhere : VERT
```

Confirmé par exécution ciblée puis par `./mvnw clean verify` complet :

```
Tests run: 129, Failures: 1, Errors: 0
BUILD FAILURE
```

Le seul échec restant est `ArchitectureRulesTest.domain_must_not_depend_on_jakarta_persistence`
(272 violations, 21/21 entités) — **explicitement hors périmètre de 3b**,
scindé module par module pour 3c à venir, inchangé par cet incrément.
`ModularityTests` et `ArchitectureRulesTest.modules_must_be_free_of_cycles`
(corrigés en 3a) restent verts. Aucune régression, aucune assertion de
test existant modifiée — 129 tests avant/après cet incrément (aucun test
ajouté : le refactor ne change aucun comportement observable, uniquement
le mécanisme de câblage interne, donc rien de nouveau à caractériser).

## 5. Fichiers modifiés

**Production uniquement** (aucun fichier de test) :
- `config/ApplicationRequestFilter.java`
- `config/FlickrConfiguration.java`
- `controller/AuthenticationController.java`
- `services/auth/ApplicationUserDetailsService.java`
- `services/impl/FlickrServiceImpl.java`
- `utils/JwtUtil.java`

**Docs** :
- `docs/migration-notes.md` — l'entrée Phase 2 sur l'injection par champ
  marquée résolue.

## 6. Zones d'ombre / décisions prises en cours de route

Aucune. Incrément mécanique, sans ambiguïté fonctionnelle rencontrée.

## 7. Prochaine étape

Incrément 3b terminé et vérifié vert sur son périmètre. **Ne pas enchaîner
sur 3c (séparation JPA/domaine, à scinder module par module, organization
en premier) sans feu vert explicite**, conformément à la règle de travail.
