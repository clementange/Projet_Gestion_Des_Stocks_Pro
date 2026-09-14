-- V3__seed_baseline_rbac_permissions.sql
--
-- Phase 14 (cutover RBAC) : amorce le catalogue de permissions necessaires aux actions
-- d'ecriture desormais protegees (Sale/CustomerOrder/PurchaseOrder/StockTransfer), et un role
-- "ADMINISTRATEUR" qui les regroupe toutes. C'est une transformation de DONNEES (permissions et
-- roles restent des lignes de table, pas des constantes Java) : AuthorizationService continue de
-- n'interroger que user_role_assignment/role/permission, aucune permission n'est codee en dur
-- dans le code applicatif (interdiction section 62 du prompt maitre).
--
-- Idempotent par construction : ON CONFLICT DO NOTHING sur permission.code (contrainte unique
-- deja existante, uka7ujv987la0i7a0o91ueevchc) pour tolerer un code deja cree par des tests
-- anterieurs (ex. REPORTING_VIEW, deja present sur la base de dev), et WHERE NOT EXISTS sur le
-- role ADMINISTRATEUR (pas de contrainte unique sur role.code dans le schema existant).

INSERT INTO public.permission (id, creation_date, code, description)
VALUES
    (nextval('public.permission_seq'), now(), 'SALE_CREATE', 'Enregistrer une vente comptant'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_RESERVE', 'Reserver le stock d''une commande client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_DELIVER', 'Livrer une commande client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_CANCEL', 'Annuler une commande client'),
    (nextval('public.permission_seq'), now(), 'PURCHASE_ORDER_RECEIVE', 'Receptionner une ligne de commande fournisseur'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_SHIP', 'Expedier un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_RECEIVE', 'Receptionner un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'REPORTING_VIEW', 'Consulter le reporting stock/ventes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role (id, creation_date, code, name, description)
SELECT nextval('public.role_seq'), now(), 'ADMINISTRATEUR', 'Administrateur',
       'Role d''amorcage attribue automatiquement au premier utilisateur d''une organisation (EntrepriseServiceImpl), regroupe toutes les permissions d''ecriture protegees'
WHERE NOT EXISTS (SELECT 1 FROM public.role WHERE code = 'ADMINISTRATEUR');

INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM public.role r
CROSS JOIN public.permission p
WHERE r.code = 'ADMINISTRATEUR'
  AND p.code IN ('SALE_CREATE', 'CUSTOMER_ORDER_RESERVE', 'CUSTOMER_ORDER_DELIVER', 'CUSTOMER_ORDER_CANCEL',
                 'PURCHASE_ORDER_RECEIVE', 'STOCK_TRANSFER_SHIP', 'STOCK_TRANSFER_RECEIVE', 'REPORTING_VIEW')
ON CONFLICT DO NOTHING;
