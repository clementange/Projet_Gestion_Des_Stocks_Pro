# Phase 5b-2i — Secrets : plus aucune valeur par défaut sensible — Rapport

## 1. Investigation (grep exhaustif)

Fichiers passés en revue : `src/main/resources/application.yml`, `docker-compose.yml`,
`Dockerfile`, `.env.example`, `src/test/resources/` (vide avant cet incrément).

| Secret | Valeur par défaut trouvée | Gravité |
|---|---|---|
| `JWT_SECRET` | **une véritable clé HS256 de 64 caractères, en clair** | **critique** — signe tous les jetons |
| `DB_PASSWORD` | `postgres` | élevée |
| `MINIO_SECRET_KEY` | `minioadmin` | élevée |
| `MINIO_ACCESS_KEY` | `minioadmin` | élevée |
| `docker-compose` `POSTGRES_PASSWORD` / `DB_PASSWORD` | `postgres` (forme `:-`) | élevée |
| `DB_USERNAME`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `SERVER_PORT`, `CORS_ALLOWED_ORIGINS`, `MINIO_ENDPOINT`, `MINIO_BUCKET` | valeurs par défaut | **aucune** — ce ne sont pas des secrets, conservées |

`Dockerfile` : aucun secret. `docker-compose` exigeait déjà `JWT_SECRET` via la forme `:?`.

## 2. Décision de conception : défaut **vide** plutôt que placeholder sans défaut

Retirer complètement le défaut (`${JWT_SECRET}`) produit, à l'absence de la variable, une erreur de
placeholder non résolu : message illisible, et Spring s'arrête à **la première** variable
manquante. J'ai donc gardé un défaut **vide** (`${JWT_SECRET:}`) — qui n'est pas une valeur
sensible — pour que le démarrage atteigne une validation dédiée capable de lister **toutes** les
variables manquantes d'un coup, avec un message actionnable.

`config/RequiredSecretsValidator` (un `InitializingBean`, donc exécuté au démarrage du contexte)
vérifie : `JWT_SECRET` présent **et ≥ 32 octets** (HS256), `DB_PASSWORD`, `MINIO_ACCESS_KEY`,
`MINIO_SECRET_KEY` présents. Alternative écartée : `@ConfigurationProperties` + `@Validated`, qui
aurait imposé de restructurer quatre propriétés déjà consommées à des endroits différents
(`spring.datasource.password` est lu par Spring Boot lui-même) pour un gain nul.

**Aucun secret n'est journalisé** : le message ne contient que des **noms** de variables. Un test
le vérifie explicitement.

## 3. Tests (5, tous verts)

`config/RequiredSecretsValidatorTest` utilise `ApplicationContextRunner` : c'est bien le
**démarrage d'un contexte Spring** qui échoue, pas une méthode appelée à la main — tout en restant
à 0,7 s (aucune base, aucun serveur).

| Test | Vérifie |
|---|---|
| `contextStartsWhenEverySecretIsProvided` | le contexte démarre quand tout est fourni |
| `contextRefusesToStartWithoutJwtSecret` | échec + message `JWT_SECRET est absent` |
| `contextRefusesToStartWithTooShortJwtSecret` | échec + message `trop court` |
| `contextRefusesToStartWithoutDatabasePasswordOrMinioKeys` | les **trois** manquants sont listés ensemble |
| `failureMessageNeverLeaksASecretValue` | une valeur de secret ne peut pas apparaître dans le message |

Note d'honnêteté sur le rouge→vert : cet incrément **ajoute** une protection, il ne corrige pas un
comportement observable existant. Le « rouge » pertinent est que ces tests échouent sans
`RequiredSecretsValidator` (il n'existait pas) ; je ne les ai pas exécutés contre un arbre sans le
validateur, puisqu'ils référencent la classe. Limite assumée plutôt que rouge inventé.

## 4. Profils et documentation

- `src/test/resources/application.properties` (nouveau) : fournit `jwt.secret` et des valeurs de
  remplissage pour les trois autres. Le datasource et MinIO restent de toute façon écrasés
  dynamiquement par Testcontainers (`AbstractIntegrationTest`). Aucune de ces valeurs n'ouvre quoi
  que ce soit.
- `.env.example` réécrit : les quatre secrets y sont **vides**, avec la commande de génération
  (`openssl rand -base64 48`) et l'avertissement de ne pas réutiliser l'ancienne clé.
- `docker-compose.yml` : `DB_PASSWORD` passe de `:-postgres` à `:?DB_PASSWORD must be set`, et
  `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` sont ajoutés sous la même forme.

## 5. ⚠ Action humaine requise, hors de mon périmètre

**L'ancienne clé `JWT_SECRET` par défaut est dans l'historique git.** Elle doit être considérée
**compromise** et **rotée avant toute mise en production** ; tout jeton signé avec elle doit être
invalidé. Je ne l'ai pas réécrite dans l'historique (opération destructive, hors périmètre) et je
ne la reproduis pas ici. Le même raisonnement vaut pour les mots de passe `postgres` /
`minioadmin` si un environnement les a jamais utilisés.

## 6. Critères de sortie

| Critère | Statut |
|---|---|
| Aucune valeur par défaut sensible dans le profil principal | **Vérifié** — grep ciblé : aucun résultat |
| Secrets retirés de `docker-compose` | **Vérifié** — forme `:?` partout |
| Échec au démarrage (fail-fast) si secret absent/faible, message clair | **Vérifié** — 4 tests de contexte |
| JWT ≥ 32 octets imposé | **Vérifié** |
| Tests fournissent leurs valeurs par un fichier dédié | **Vérifié** |
| `.env.example` documente tout | **Vérifié** |
| Aucune vraie clé dans un rapport ou un commit | **Vérifié** — seuls des noms de variables |
| Suite complète verte | **Vérifié** — 262 tests (257 avant) |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |

## 7. Limites assumées

- **`DB_USERNAME` garde son défaut `postgres`.** Ce n'est pas un secret ; le prompt ne le listait
  pas. Discutable en défense en profondeur, non traité.
- **Pas de rotation effective** de la clé compromise : action humaine (voir §5).
- **Pas de scan d'historique git** pour d'autres secrets ayant pu transiter (les clés Flickr
  supprimées en 4c y sont aussi, déjà documenté dans `migration-notes`). Non fait ici.
- **Le validateur n'est pas exercé en conditions réelles de production** (seulement en contexte de
  test réduit) : rien ne garantit qu'un déploiement réel n'ait pas d'autre source de propriétés
  (Vault, config server) contournant ces placeholders.

## 8. Backlog découvert

- `application.yml` contient encore des commentaires évoquant des valeurs « à changer avant la
  prod » pour MinIO ; un profil `prod` explicite (avec `spring.profiles`) n'existe pas. Aucun
  mécanisme n'empêche aujourd'hui de démarrer en production avec la configuration de dev
  (hors secrets, désormais obligatoires).
