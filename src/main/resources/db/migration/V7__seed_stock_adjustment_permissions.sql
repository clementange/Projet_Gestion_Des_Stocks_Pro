-- V7__seed_stock_adjustment_permissions.sql
--
-- Phase 5b-2d : InventoryController.receive/issue/correct (correction directe de stock) n'avaient
-- jusqu'ici aucune verification de permission. Ferme ce trou avec le meme motif que V3/V4 : trois
-- permissions dediees (une par action, coherent avec le grain deja utilise pour
-- STOCK_TRANSFER_SHIP/RECEIVE), accordees au role ADMINISTRATEUR - sinon tout admin de tenant
-- existant, y compris ceux deja amorces, se retrouverait immediatement bloque sur ces trois
-- endpoints. Transformation de DONNEES uniquement, meme idempotence (ON CONFLICT DO NOTHING).

INSERT INTO public.permission (id, creation_date, code, description)
VALUES
    (nextval('public.permission_seq'), now(), 'STOCK_RECEIVE', 'Enregistrer une entree de stock manuelle'),
    (nextval('public.permission_seq'), now(), 'STOCK_ISSUE', 'Enregistrer une sortie de stock manuelle'),
    (nextval('public.permission_seq'), now(), 'STOCK_CORRECT', 'Corriger la quantite de stock d''un article sur un site')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM public.role r
CROSS JOIN public.permission p
WHERE r.code = 'ADMINISTRATEUR'
  AND p.code IN ('STOCK_RECEIVE', 'STOCK_ISSUE', 'STOCK_CORRECT')
ON CONFLICT DO NOTHING;
