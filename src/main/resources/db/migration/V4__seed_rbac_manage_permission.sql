-- V4__seed_rbac_manage_permission.sql
--
-- Phase 15 : les endpoints d'administration RBAC (attribution de roles a un utilisateur) etaient
-- restes deliberement non proteges en Phase 14 (voir docs/ARCHITECTURE.md §10). Ferme ce trou :
-- POST/DELETE sur user-role-assignments/roles/permissions exigent desormais la permission
-- RBAC_MANAGE en perimetre GLOBAL. Comme en V3, une transformation de DONNEES uniquement.

INSERT INTO public.permission (id, creation_date, code, description)
VALUES (nextval('public.permission_seq'), now(), 'RBAC_MANAGE', 'Administrer roles, permissions et affectations')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM public.role r
CROSS JOIN public.permission p
WHERE r.code = 'ADMINISTRATEUR'
  AND p.code = 'RBAC_MANAGE'
ON CONFLICT DO NOTHING;
