# Phase 5f — `UserRoleAssignment.userId` doit appartenir au tenant de l'appelant — Rapport

Ferme le dernier point de la classe « référence non vérifiée » ouvert par 5d/5e.

## 1. Gravité réelle — mesurée, pas supposée

Le backlog annonçait « B peut attribuer un rôle de son organisation à un utilisateur de A ». C'est
exact, mais **ce n'est pas une escalade de privilèges**, et il faut le dire avant de décrire le
correctif pour que personne ne croie avoir fermé une faille d'accès.

`AuthorizationServiceImpl.hasPermission` ne retient que les affectations dont
l'`organizationId` égale celui de **l'appelant** :

```java
private List<UserRoleAssignment> assignmentsForOrganization(Long userId, Long organizationId) {
  return userRoleAssignmentRepository.findAllByUserId(userId).stream()
      .filter(assignment -> organizationId.equals(assignment.getOrganizationId()))
      ...
```

Et `organizationId` est imposé depuis le principal depuis 5b-2f. Une affectation créée par B pour
un utilisateur de A porte donc l'organisation de **B** ; quand cet utilisateur de A s'authentifie,
son `organizationId` résout **A**, et l'affectation ne s'applique jamais. Elle est **inerte**.
Le test `theForeignGrantWasNeverEffectiveAnyway` documente explicitement cette frontière.

Ce qui était réellement en jeu :

| Enjeu | Détail |
|---|---|
| **Intégrité du référentiel RBAC** | des lignes référençant des utilisateurs étrangers à l'organisation — et, l'existence n'étant pas vérifiée non plus, des lignes référençant **aucun utilisateur** (un entier arbitraire suffisait) |
| **Risque dormant** | un utilisateur déplacé de tenant, ou un futur chemin de lecture moins strictement filtré, rendrait ces lignes actives. L'isolation reposait entièrement sur un filtre **en aval** |

D'où un refus **à la création** (fail-closed) plutôt qu'une confiance dans le filtrage ultérieur.

## 2. Rouge avant correctif

```
bCannotAssignARoleToAUserOfTenantA             Status expected:<404> but was:<200>
nobodyCanAssignARoleToAUserThatDoesNotExistAtAll  Status expected:<404> but was:<200>
```

Les deux autres tests du fichier (cas légitime + caractérisation de la gravité) passaient déjà
avant le correctif : la preuve que celui-ci ne casse pas le chemin normal était donc acquise
d'avance.

## 3. Correctif

Même structure qu'en 5d. `UserRoleAssignmentService` expose désormais deux variantes :

- `save(dto)` — **inchangée**, sans contrôle, nécessaire à l'**amorçage** d'un tenant
  (`TenantRegistrationServiceImpl` accorde `ADMINISTRATEUR` à l'admin qu'il vient de créer, sans
  principal donc sans tenant appelant) et utilisée par les fixtures de test qui forgent des
  utilisateurs synthétiques ;
- `save(dto, callerTenantId)` — utilisée par le point d'entrée HTTP, qui recharge l'utilisateur
  cible via `userService.findById(userId, callerTenantId)`, déjà scopé, et renvoie donc un
  `USER_NOT_FOUND` masqué : pour l'appelant, un utilisateur d'un autre tenant n'existe pas.

Le scope est `User.idEntreprise` (Tenant.id) et **non** `organizationId` : c'est la colonne qui
rattache un utilisateur à son tenant — même distinction qu'en 5b-2c.

## 4. ⚠ Le piège que mon premier correctif a introduit

Ma première version n'annotait pas la nouvelle méthode. Or :

- la classe est `@Transactional(readOnly = true)` ;
- `save(dto, callerTenantId)` héritait donc du **read-only** ;
- son appel à `save(dto)` est une **auto-invocation**, qui court-circuite le proxy Spring : le
  `@Transactional` read-write porté par `save(dto)` n'était **jamais appliqué**.

Résultat : **l'insertion était silencieusement abandonnée au commit**. La réponse HTTP renvoyait
bien `{"id":3,...,"organizationId":2}` — l'id avait été alloué par la séquence — mais la ligne
n'existait pas. Toute attribution de rôle faite par l'API aurait disparu sans aucune erreur.

Détecté par `CreateEndpointIdInjectionHttpIntegrationTest`, qui **relit** l'affectation après
l'avoir créée. J'ai d'abord cherché la cause dans le chemin de lecture, que je n'avais pas touché ;
c'est en remettant les sources `main` d'avant 5f et en constatant le vert que j'ai confirmé que le
défaut venait bien de moi.

**Corrigé** par `@Transactional` sur la nouvelle méthode, avec un commentaire qui explique
pourquoi la ligne est indispensable — sans quoi un futur nettoyage la supprimerait comme
redondante.

**Deux conséquences tirées :**

1. **Mon propre test ne l'aurait pas vu** : il n'assertait que la réponse HTTP. Il **relit**
   désormais l'affectation créée. Une assertion sur la réponse d'un POST ne prouve pas la
   persistance.
2. **Audit des autres occurrences possibles du piège** (fait, voir §5).

## 5. Audit du même piège ailleurs

| Classe | `@Transactional(readOnly = true)` au niveau classe ? | Surcharge déléguant en interne ? | Verdict |
|---|---|---|---|
| `UserRoleAssignmentServiceImpl` | oui | oui | **corrigé** |
| `RoleServiceImpl` | oui | non — `save`/`delete` portent chacune `@Transactional` | sain |
| `AuthorizationServiceImpl` | oui | lecture seule par nature | sain |
| `UserServiceImpl` (surcharges de 5d) | **non** (aucune annotation de classe) | oui | sain — pas de read-only hérité |

Aucune autre occurrence. Les six services CRUD de 5c délèguent aussi
(`delete(id, userId, organizationId)` → `delete(id)`) mais aucun n'est `readOnly` au niveau classe.

## 6. Critères de sortie

| Critère | Statut |
|---|---|
| Faille reproduite en rouge avant correctif | **Vérifié** — §2 |
| Gravité réelle mesurée, pas supposée | **Vérifié** — §1, avec test dédié |
| Utilisateur cible rechargé via le finder scopé | **Vérifié** |
| Utilisateur inexistant également refusé | **Vérifié** |
| Amorçage de tenant toujours fonctionnel | **Vérifié** — variante interne inchangée |
| Contre-épreuve : le cas légitime marche **et persiste** | **Vérifié** — relecture après création |
| Audit du piège `readOnly` + auto-invocation | **Vérifié** — §5 |
| Suite complète verte | **Vérifié** — **280 tests** (276 avant), 0 échec |
| `ArchitectureRulesTest` / `ModularityTests` verts | **Vérifié** |
| Aucun test désactivé, aucune assertion affaiblie | **Vérifié** — aucun test existant modifié |

## 7. Limites assumées

- **`DELETE /user-role-assignments/delete/{id}` n'est pas concerné** : l'appartenance de
  l'affectation elle-même est déjà vérifiée depuis 5b-2h (`findById(id, organizationId)` avant
  `requireRbacManage`). Vérifié, rien à faire.
- **Les affectations déjà créées avec un `userId` étranger ou inexistant ne sont pas détectées.**
  Même réserve qu'en 5d/5e : requête d'audit à passer si l'API a déjà été exposée. Elle est simple
  ici — joindre `user_role_assignment` à `utilisateur` et lister les lignes dont l'`idEntreprise`
  de l'utilisateur ne correspond pas au tenant de l'organisation de l'affectation, ou dont
  l'utilisateur est absent.
- **L'inventaire des références reste manuel** (limite déjà posée en 5e) :
  `ForeignReferenceInjectionTest` ne couvre pas `/user-role-assignments/create`, dont la
  vérification est prouvée par son propre test. Un inventaire dérivé du
  `RequestMappingHandlerMapping` les réunirait — toujours au backlog.
