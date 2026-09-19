-- Phase 5b-1 : corrige le contournement RBAC cross-tenant (voir docs/phase-5b1-report.md).
-- AuthorizationServiceImpl.hasPermission traitait ScopeType.GLOBAL comme litteralement global a
-- toute l'application, sans aucune frontiere d'organisation : n'importe quel administrateur de
-- tenant (bootstrap avec un role GLOBAL, voir TenantRegistrationServiceImpl) pouvait agir sur les
-- ressources de n'importe quel AUTRE tenant. organization_id, nullable comme customer.organization_id/
-- supplier.organization_id (V5), impose desormais au niveau applicatif (UserRoleAssignmentValidator),
-- pas au niveau base.

ALTER TABLE public.user_role_assignment
    ADD COLUMN organization_id bigint;

ALTER TABLE ONLY public.user_role_assignment
    ADD CONSTRAINT fk_user_role_assignment_organization FOREIGN KEY (organization_id) REFERENCES public.organization(id);
