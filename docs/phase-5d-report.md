# Phase 5d — `POST /users/create` n'accepte plus le tenant depuis le corps — Rapport

Ferme le point n°1 du backlog de `docs/lot1-final-report.md`, classé gravité élevée.

## 1. La faille, reproduite avant tout correctif

`UserDto.idEntreprise` était lu depuis le **corps de la requête** et écrit tel quel par
`UserDto.toEntity`. L'administrateur du tenant B pouvait donc créer un compte — **avec le mot de
passe de son choix** — directement dans le tenant A. Une fois authentifié, ce compte résolvait
l'`organizationId` de A (`ApplicationUserDetailsService.resolveOrganizationId(user.getIdEntreprise())`) :
ce n'est pas une fuite de lecture, c'est une **porte d'entrée permanente et réutilisable** dans le
tenant d'autrui.

Rouge observé avant correctif, sur `UserCreateTenantIsForcedFromPrincipalTest` :

```
bCannotPlantAUserInsideTenantAByPassingItsIdEntreprise
  idEntreprise doit venir du principal, pas du corps de la requete expected:<2> but was:<1>
aUserCreatedWithoutIdEntrepriseLandsInTheCallerTenant
  expected:<3> but was:<0>
```

`expected:<2> but was:<1>` est l'exploit lui-même : l'admin du tenant 2 a bien implanté un compte
dans le tenant 1.

### Pourquoi les garde-fous existants ne l'ont pas vu

Trois mécanismes auraient pu l'attraper, aucun ne pouvait :

| Mécanisme | Pourquoi il passe à côté |
|---|---|
| `CrossTenantEndpointMatrixTest` (5b-2h) | elle sonde l'**id de la ressource visée**, jamais la valeur d'un **champ du corps**. Déjà noté comme limite dans son rapport, c'en est la première conséquence concrète |
| `hasPermission(USER_CREATE)` (5c) | il passe **légitimement** : B a bien le droit de créer des utilisateurs. Simplement pas chez A. Illustration exacte de « la permission ne prouve jamais l'appartenance » |
| `CreationIdGuard` (5b-2f) | il surveille `dto.getId()`, pas `dto.getIdEntreprise()` |

C'est un défaut d'**appartenance sur un champ**, une classe que le lot 1 n'avait pas couverte : il
avait traité l'appartenance de la *ressource ciblée*, pas celle des *références qu'on lui donne*.

## 2. Correctif

`UserService.save(dto, callerTenantId, userId, organizationId)` — la variante gardée du point
d'entrée HTTP reçoit désormais le tenant de l'appelant et **écrase** `dto.setIdEntreprise(...)`
avant de déléguer. La valeur du corps n'est plus jamais lue.

```java
requirePermission(userId, "USER_CREATE", organizationId);
if (callerTenantId == null) { /* fail-closed, USER_ACCESS_DENIED */ }
dto.setIdEntreprise(callerTenantId);
return save(dto);
```

**Forcer plutôt que rejeter.** Même posture qu'en 5b-2h pour `/cities/filter/organization/{id}`
(« seul le principal fait foi ») et non celle de `CREATION_ID_FORBIDDEN`. Un `id` fourni sur une
création est une erreur de protocole sans ambiguïté ; envoyer **son propre** `idEntreprise` est au
contraire un usage légitime et répandu (quatre tests du dépôt le font). Rejeter toute valeur
casserait des appelants corrects sans rien gagner, puisque la seule valeur qu'un appelant puisse
légitimement exprimer est celle qu'on lui impose.

**Fail-closed sur un appelant sans tenant** : il n'a aucun tenant où créer, et le laisser passer
produirait un compte orphelin, invisible aux lectures scopées. `USER_ACCESS_DENIED`.

**L'amorçage de tenant n'est pas touché** : `TenantRegistrationServiceImpl` appelle la variante
interne `save(dto)`, seule autorisée à choisir le tenant — c'est elle qui crée le tout premier
admin, avant qu'aucun tenant ne soit résolvable depuis un principal.

### Surface auditée

`UserDto.toEntity` n'a **qu'un seul appelant** (`UserServiceImpl.save(dto)`), atteint uniquement
par la variante gardée (qui force désormais) ou par l'amorçage. `changePassword` et `updatePhoto`
mutent l'entité **chargée** depuis la base (correctif de la Phase 4c, maintenu en 5b-2g) : ils ne
peuvent pas réécrire `idEntreprise`. La surface est close.

Vérifié aussi que ce défaut ne se généralise pas : les autres ressources sont scopées par
`organization` (forcée depuis le principal depuis 5b-2a/2c), pas par `idEntreprise`. Seul
`User.idEntreprise` servait à une décision d'accès, via la résolution de l'`organizationId` au
login.

## 3. Tests

`identity/UserCreateTenantIsForcedFromPrincipalTest` (nouveau, 2 tests rouges→verts) :

| Test | Vérifie |
|---|---|
| `bCannotPlantAUserInsideTenantAByPassingItsIdEntreprise` | le compte créé appartient à B ; l'admin de A ne le voit pas dans `/users/all` ; et une fois authentifié il résout l'organisation de **B** — les trois angles, pas seulement le code retour |
| `aUserCreatedWithoutIdEntrepriseLandsInTheCallerTenant` | un corps sans `idEntreprise` ne produit plus de compte orphelin |

## 4. Tests existants modifiés — changement de comportement assumé

Deux tests créaient volontairement un utilisateur **sans tenant** via HTTP, ce que le correctif
rend impossible par construction. Dans les deux cas **l'assertion est inchangée** : seule la
fixture change, et elle passe par la variante interne du service — le seul chemin qui produise
encore ce cas.

| Test | Modification | Pourquoi ce n'est pas une assertion affaiblie |
|---|---|---|
| `security/SecurityFilterChainIntegrationTest.userWithoutEntrepriseCanStillLogIn` | fixture → `userService.save(dto)` | il verrouille une **NullPointerException réelle** de `ApplicationUserDetailsService`/`JwtUtil` sur un utilisateur sans Entreprise, trouvée lors d'un audit curl manuel. Supprimer le test aurait perdu cette couverture ; la garder via le service la préserve intégralement |
| `identity/AuthenticationCharacterizationTest.loginSucceedsAndJwtCarriesExpectedClaims` | `assertNull(extractIdEntreprise)` → `assertNotNull`, **et** le cas limite extrait dans un test dédié `loginOfATenantlessUserCarriesNoIdEntrepriseClaim` qui garde l'`assertNull` d'origine | le comportement caractérisé a **délibérément changé** : un utilisateur créé par HTTP a maintenant toujours un tenant. Plutôt que d'adapter l'assertion et perdre le cas, je l'ai scindé en deux — le nouveau comportement et l'ancien cas limite sont tous deux couverts |

## 5. Critères de sortie

| Critère | Statut |
|---|---|
| Faille reproduite par un test rouge avant correctif | **Vérifié** — sortie du rouge citée en §1 |
| `idEntreprise` imposé depuis le principal, corps ignoré | **Vérifié** |
| Amorçage de tenant toujours fonctionnel | **Vérifié** — variante interne inchangée, `tenants/register` vert |
| Appelant sans tenant refusé (fail-closed) | **Vérifié** |
| Audit des autres chemins d'écriture de `User.idEntreprise` | **Vérifié** — §2 |
| Suite complète verte | **Vérifié** — **274 tests** (271 avant), 0 échec |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test désactivé ; chaque test modifié justifié | **Vérifié** — §4 |

## 6. Limites assumées

- **Les comptes déjà implantés ne sont pas détectés.** Le correctif ferme la porte ; il ne fait pas
  l'inventaire de ce qui a pu passer avant. Une requête de contrôle (utilisateurs dont le
  `idEntreprise` ne correspond à aucun créateur plausible) relèverait d'un audit de données, hors
  périmètre de ce correctif. **À faire avant mise en production si l'API a déjà été exposée.**
- **La matrice inter-tenant ne sonde toujours pas les champs du corps.** C'est la cause racine de
  l'angle mort, et elle reste ouverte : les autres références imbriquées
  (`CustomerOrder.customerId`, `PurchaseOrder.supplierId`, `Article.category`, l'`article` des
  lignes) sont encore non vérifiées. Toujours au backlog, gravité moyenne — ce correctif n'a traité
  que `User.idEntreprise`, le seul qui servait à une décision d'accès.
- **Pas de test prouvant qu'un appelant sans tenant est refusé sur `/users/create`.** Le chemin est
  fail-closed dans le code et couvert par lecture, pas par un test dédié : fabriquer ce principal
  demande un utilisateur sans tenant **et** porteur de `USER_CREATE`, soit une affectation RBAC
  dans une organisation qu'il n'a pas. Écart signalé plutôt que maquillé.
