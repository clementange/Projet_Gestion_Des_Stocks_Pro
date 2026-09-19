# Phase 5b-1 — frontière tenant dans la couche RBAC — Rapport

Corrige le contournement cross-tenant trouvé en investiguant le leak "read-only" initialement
visé par 5b : `AuthorizationServiceImpl.hasPermission` traitait `ScopeType.GLOBAL` comme global à
**toute l'application**, pas à la seule organisation de l'affectation RBAC. Tout admin de tenant
(bootstrap avec un rôle GLOBAL depuis Phase 4b) pouvait donc agir — en écriture, pas seulement en
lecture — sur les ressources de n'importe quel autre tenant : `POST /purchase-orders/{id}/valider`,
`/customer-orders/{id}/livrer`, `/stock-transfers/{id}/ship`, `/roles/*`, etc.

## 0. Root cause, vérifiée avant tout code

`UserRoleAssignment` (et `Role`/`Permission`) ne portaient aucune notion d'organisation.
`AuthorizationServiceImpl.hasPermission` résout les affectations par `userId` seul
(`findAllByUserId`), et `assignmentAppliesToScope` retournait `true` sans condition pour toute
affectation `GLOBAL`, quel que soit le `scopeId` demandé. Le JWT porte `idEntreprise`
(`Tenant.id`), jamais utilisé par la couche RBAC ; `ApplicationRequestFilter` ne l'exploite que
pour le `MDC` consommé par `Interceptor.java` (les 10 tables legacy), pas pour l'autorisation.
Confirmé : aucune frontière tenant n'existait nulle part dans la chaîne d'autorisation.

## 1. Conception retenue (validée avant implémentation)

`organizationId` ajouté à `UserRoleAssignment` — chaque affectation appartient désormais à une
organisation. `AuthorizationServiceImpl` filtre les affectations du caller par cet
`organizationId` **avant** d'appliquer la logique GLOBAL/scope existante : GLOBAL reste "partout",
mais seulement parmi les affectations qui appartiennent à l'organisation de l'appelant.
L'organisation de l'appelant est résolue une fois au login (`ApplicationUserDetailsService`, via
le détour `Tenant.id -> Tenant.organizationId` déjà utilisé par le bootstrap RBAC en Phase 4b) et
portée sur `ExtendedUser`/le JWT, au même titre qu'`idEntreprise`/`idUtilisateur`.

## 2. Ce qui a changé

- **Migration** `V6__add_organization_id_to_user_role_assignment.sql` : `organization_id` (FK
  vers `organization`, nullable en base comme `customer.organization_id`/`supplier.organization_id`
  en V5) ; imposé non-null au niveau applicatif par `UserRoleAssignmentValidator`.
- **`AuthorizationService`** : `hasPermission`/`hasGlobalAccess` gagnent un paramètre
  `organizationId` ; filtrage des affectations avant toute autre logique.
- **JWT/auth** : `JwtUtil` (nouveau claim `organizationId`), `ExtendedUser` (nouveau champ),
  `ApplicationUserDetailsService` (résolution via `TenantService`, tolérante comme pour
  `idEntreprise` — un `User` sans organisation reste authentifiable, `organizationId` vaut alors
  `null` et n'accorde donc aucune permission nécessitant une frontière).
- **`TenantRegistrationServiceImpl`** : l'affectation ADMINISTRATEUR/GLOBAL bootstrap porte
  désormais `organizationId`.
- **9 points d'appel** de `hasPermission`/`hasGlobalAccess` mis à jour, `organizationId` résolu
  dans le contrôleur (`principal.getOrganizationId()`) et threadé jusqu'au service :
  `RoleController`, `PermissionController`, `UserRoleAssignmentController` (leur
  `requireRbacManage`), `SaleServiceImpl.create()`, `CustomerOrderServiceImpl` (reserve/deliver/
  cancel), `PurchaseOrderServiceImpl.receiveLine()`, `StockTransferServiceImpl` (ship/receive),
  `ReportingServiceImpl` (4 méthodes).

## 3. Tests

Le test qui prouve directement la correction :
`identity.AuthorizationServiceIntegrationTest.globalAssignmentInOneOrganizationDoesNotGrantAccessInAnother`
— une affectation GLOBAL créée dans une organisation A n'accorde ni `hasGlobalAccess` ni
`hasPermission` lorsqu'on interroge avec l'organisation B. Avant ce correctif, les deux auraient
répondu `true` sans condition.

Les 2 tests préexistants du même fichier caractérisaient déjà le bon comportement *dans une seule
organisation* (responsable d'entrepôt limité à son entrepôt, Directeur Général global) — adaptés
pour passer un `organizationId` réel plutôt qu'un littéral fabriqué (la nouvelle contrainte FK sur
`organization_id` l'exige).

8 fichiers de test existants (`SaleServiceIntegrationTest`, `CustomerOrderServiceIntegrationTest`,
`PurchaseOrderServiceIntegrationTest`, `StockTransferServiceIntegrationTest`,
`ReportingServiceIntegrationTest`, `CrossModuleConcurrencyIntegrationTest`,
`CrossModuleEndToEndIntegrationTest`, `AuthorizationServiceIntegrationTest`) adaptés pour fournir
`organizationId` à chaque affectation créée et chaque appel de service — mécanique, pas de
changement de ce qu'ils vérifient.

## 4. Critère de sortie

| Critère | Résultat |
|---|---|
| GLOBAL borné à l'organisation de l'affectation | **Vérifié** — test dédié, voir §3. |
| Comportement intra-organisation inchangé | **Vérifié** — les 2 tests préexistants passent toujours avec les mêmes assertions. |
| `organizationId` résolu de façon fiable (login, pas JWT re-decodé) | **Vérifié** — `ApplicationUserDetailsService` re-résout à chaque requête via `loadUserByUsername`, cohérent avec `idEntreprise`/`idUtilisateur`. |
| `ArchitectureRulesTest`/`ModularityTests` verts | **Vérifié**. |
| Tous les tests verts | **Vérifié** — 206 tests, 0 échec, 0 erreur (205 avant l'incrément, +1 test dédié ; les autres modifications de fichiers existants n'ajoutent pas de méthode). |

## 5. Fichiers modifiés

**Production** : `db/migration/V6__add_organization_id_to_user_role_assignment.sql` (nouveau),
`identity/domain/model/UserRoleAssignment.java`, `META-INF/orm.xml`,
`identity/application/dto/UserRoleAssignmentDto.java`,
`identity/application/validator/UserRoleAssignmentValidator.java`,
`identity/application/AuthorizationService.java`,
`identity/application/impl/AuthorizationServiceImpl.java`, `model/auth/ExtendedUser.java`,
`utils/JwtUtil.java`, `services/auth/ApplicationUserDetailsService.java`,
`tenant/application/impl/TenantRegistrationServiceImpl.java`,
`identity/presentation/rest/{RoleController,PermissionController,UserRoleAssignmentController}.java`,
`sales/application/{SaleService,impl/SaleServiceImpl}.java`,
`sales/presentation/rest/SaleController.java`,
`sales/application/{CustomerOrderService,impl/CustomerOrderServiceImpl}.java`,
`sales/presentation/rest/CustomerOrderController.java`,
`purchasing/application/{PurchaseOrderService,impl/PurchaseOrderServiceImpl}.java`,
`purchasing/presentation/rest/PurchaseOrderController.java`,
`transfers/application/{StockTransferService,impl/StockTransferServiceImpl}.java`,
`transfers/presentation/rest/StockTransferController.java`,
`reporting/application/{ReportingService,impl/ReportingServiceImpl}.java`,
`reporting/presentation/rest/ReportingController.java`.

**Test** : `identity/AuthorizationServiceIntegrationTest.java`,
`sales/SaleServiceIntegrationTest.java`, `sales/CustomerOrderServiceIntegrationTest.java`,
`purchasing/PurchaseOrderServiceIntegrationTest.java`,
`transfers/StockTransferServiceIntegrationTest.java`,
`reporting/ReportingServiceIntegrationTest.java`,
`integration/CrossModuleConcurrencyIntegrationTest.java`,
`integration/CrossModuleEndToEndIntegrationTest.java`.

**Docs** : `docs/migration-notes.md`.

## 6. Prochaine étape

5b-1 terminé et vérifié vert. Reste ouvert : 5b-2 (les endpoints `findAll()`/`findById()` sans
aucune vérification RBAC du tout — Customer/Supplier/Sale/CustomerOrder/PurchaseOrder/Tenant/
Organization/Site/Warehouse/Stock/User/StockTransfer), et les gaps RBAC sur
`Sale`/`CustomerOrder`/`PurchaseOrder.create/validate` et `/users/*`/`/utilisateurs/*`
create/delete/find déjà identifiés en Phase 5a. **N'enchaîne pas sur un autre incrément sans feu
vert explicite.**
