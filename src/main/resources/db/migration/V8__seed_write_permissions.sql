-- V8__seed_write_permissions.sql
--
-- Phase 5c : ferme le backlog « RBAC-on-create/update/delete ». Jusqu'ici l'appartenance (tenant)
-- etait verifiee partout (5b-2*), mais pas le DROIT METIER de creer/modifier/supprimer : tout
-- membre d'une organisation pouvait tout y faire.
--
-- Meme motif que V3/V4/V7 : transformation de DONNEES uniquement, idempotente, et les nouvelles
-- permissions sont accordees au role ADMINISTRATEUR - sans quoi tout admin de tenant existant se
-- retrouverait immediatement bloque sur ces endpoints (lecon de V7).
--
-- DECISION PAR DEFAUT (voir docs/rbac-matrix.md) : AUCUN nouveau role metier n'est seede. Seul
-- ADMINISTRATEUR recoit ces permissions. Les roles standards proposes (magasinier, commercial,
-- acheteur, lecture seule) sont documentes comme recommandation, pas crees.

INSERT INTO public.permission (id, creation_date, code, description)
VALUES
    (nextval('public.permission_seq'), now(), 'ARTICLE_CREATE', 'Creer un article'),
    (nextval('public.permission_seq'), now(), 'ARTICLE_UPDATE', 'Modifier un article'),
    (nextval('public.permission_seq'), now(), 'ARTICLE_DELETE', 'Supprimer un article'),
    (nextval('public.permission_seq'), now(), 'CATEGORY_CREATE', 'Creer une categorie'),
    (nextval('public.permission_seq'), now(), 'CATEGORY_UPDATE', 'Modifier une categorie'),
    (nextval('public.permission_seq'), now(), 'CATEGORY_DELETE', 'Supprimer une categorie'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_CREATE', 'Creer un client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_UPDATE', 'Modifier un client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_DELETE', 'Supprimer un client'),
    (nextval('public.permission_seq'), now(), 'SUPPLIER_CREATE', 'Creer un fournisseur'),
    (nextval('public.permission_seq'), now(), 'SUPPLIER_UPDATE', 'Modifier un fournisseur'),
    (nextval('public.permission_seq'), now(), 'SUPPLIER_DELETE', 'Supprimer un fournisseur'),
    (nextval('public.permission_seq'), now(), 'CITY_CREATE', 'Creer une ville'),
    (nextval('public.permission_seq'), now(), 'CITY_UPDATE', 'Modifier une ville'),
    (nextval('public.permission_seq'), now(), 'CITY_DELETE', 'Supprimer une ville'),
    (nextval('public.permission_seq'), now(), 'SITE_CREATE', 'Creer un site'),
    (nextval('public.permission_seq'), now(), 'SITE_DELETE', 'Supprimer un site'),
    (nextval('public.permission_seq'), now(), 'WAREHOUSE_CREATE', 'Creer un entrepot'),
    (nextval('public.permission_seq'), now(), 'WAREHOUSE_UPDATE', 'Modifier un entrepot'),
    (nextval('public.permission_seq'), now(), 'WAREHOUSE_DELETE', 'Supprimer un entrepot'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_CREATE', 'Creer une commande client'),
    (nextval('public.permission_seq'), now(), 'PURCHASE_ORDER_CREATE', 'Creer une commande fournisseur'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_CREATE', 'Creer un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'USER_CREATE', 'Creer un utilisateur'),
    (nextval('public.permission_seq'), now(), 'USER_DELETE', 'Supprimer un utilisateur'),
    -- Transitions de machine a etats qui n'avaient que l'appartenance (5b-2h) et aucune
    -- permission. Portee SITE, comme les transitions deja gardees (RESERVE, DELIVER, SHIP...).
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_VALIDATE', 'Valider une commande client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_PREPARE', 'Preparer une commande client'),
    (nextval('public.permission_seq'), now(), 'CUSTOMER_ORDER_SHIP', 'Expedier une commande client'),
    (nextval('public.permission_seq'), now(), 'PURCHASE_ORDER_VALIDATE', 'Valider une commande fournisseur'),
    (nextval('public.permission_seq'), now(), 'PURCHASE_ORDER_CANCEL', 'Annuler une commande fournisseur'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_REQUEST', 'Demander un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_APPROVE', 'Approuver un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_PREPARE', 'Preparer un transfert de stock'),
    (nextval('public.permission_seq'), now(), 'STOCK_TRANSFER_CANCEL', 'Annuler un transfert de stock')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM public.role r
CROSS JOIN public.permission p
WHERE r.code = 'ADMINISTRATEUR'
  AND p.code IN ('ARTICLE_CREATE', 'ARTICLE_UPDATE', 'ARTICLE_DELETE',
                 'CATEGORY_CREATE', 'CATEGORY_UPDATE', 'CATEGORY_DELETE',
                 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE', 'CUSTOMER_DELETE',
                 'SUPPLIER_CREATE', 'SUPPLIER_UPDATE', 'SUPPLIER_DELETE',
                 'CITY_CREATE', 'CITY_UPDATE', 'CITY_DELETE',
                 'SITE_CREATE', 'SITE_DELETE',
                 'WAREHOUSE_CREATE', 'WAREHOUSE_UPDATE', 'WAREHOUSE_DELETE',
                 'CUSTOMER_ORDER_CREATE', 'PURCHASE_ORDER_CREATE', 'STOCK_TRANSFER_CREATE',
                 'USER_CREATE', 'USER_DELETE',
                 'CUSTOMER_ORDER_VALIDATE', 'CUSTOMER_ORDER_PREPARE', 'CUSTOMER_ORDER_SHIP',
                 'PURCHASE_ORDER_VALIDATE', 'PURCHASE_ORDER_CANCEL',
                 'STOCK_TRANSFER_REQUEST', 'STOCK_TRANSFER_APPROVE', 'STOCK_TRANSFER_PREPARE',
                 'STOCK_TRANSFER_CANCEL')
ON CONFLICT DO NOTHING;
