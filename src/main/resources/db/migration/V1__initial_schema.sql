-- V1__initial_schema.sql
--
-- Baseline Flyway migration (Phase 11 de la migration monolithe modulaire).
-- Capture l'etat du schema tel que construit par Hibernate ddl-auto=update au fil des
-- Phases 1-10 (legacy + organization + identity + catalog + inventory + purchasing + sales +
-- transfers). A partir d'ici, ddl-auto passe en "validate" : plus aucune evolution de schema
-- ne doit passer par Hibernate, uniquement par de nouveaux scripts Vn__*.sql.
--
-- Nettoyage delibere par rapport au schema brut : les colonnes "version bigint" ajoutees par
-- erreur sur les tables legacy (article, category, client, commandeclient,
-- commandefournisseur, entreprise, fournisseur, lignecommandeclient, lignecommandefournisseur,
-- lignevente, mvtstk, roles, utilisateur, ventes) lors d'un essai de verrouillage optimiste
-- global revert en Phase 2 ne sont PAS reprises ici : seul Stock (agregat inventory) porte
-- legitimement un numero de version. La sequence "hibernate_sequence", orpheline depuis le
-- passage a des sequences dediees par entite sous Hibernate 6, n'est pas reprise non plus.
--
-- Volontairement absent de cette baseline (cf plan de migration) :
--  - Suppression des colonnes "identreprise" : encore lues/ecrites par le code legacy non
--    migre (CommandeFournisseur, CommandeClient, Ventes, MvtStk, Fournisseur, Client,
--    Utilisateur, Roles). A traiter seulement apres bascule de leurs consommateurs.
--  - Backfill de organization_id/site_id depuis les donnees legacy : aucune correspondance
--    reelle Entreprise -> Organization n'existe (cette base ne contient que des donnees de
--    test), inventer une regle de rattachement par defaut serait arbitraire.
--  - Index de performance supplementaires du Livrable 5 (article.codearticle unique,
--    utilisateur.email unique, stock_movement(article_id, site_id, date_mvt), etc.) : a
--    ajouter dans une migration dediee et testee separement, pas melanges a l'adoption de
--    Flyway elle-meme.

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: article; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.article (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    codearticle character varying(255),
    designation character varying(255),
    identreprise bigint,
    photo character varying(255),
    prixunitaireht numeric(38,2),
    prixunitairettc numeric(38,2),
    tauxtva numeric(38,2),
    idcategory bigint,
    organization_id bigint
);


--
-- Name: article_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.article_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.category (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    code character varying(255),
    designation character varying(255),
    identreprise bigint,
    organization_id bigint
);


--
-- Name: category_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.category_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: city; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.city (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    country character varying(255),
    name character varying(255) NOT NULL,
    organization_id bigint NOT NULL
);


--
-- Name: city_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.city_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.client (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    adresse1 character varying(255),
    adresse2 character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    ville character varying(255),
    identreprise bigint,
    mail character varying(255),
    nom character varying(255),
    num_tel character varying(255),
    photo character varying(255),
    prenom character varying(255)
);


--
-- Name: client_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.client_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commandeclient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commandeclient (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    code character varying(255),
    datecommande timestamp without time zone,
    etatcommande character varying(255),
    identreprise bigint,
    idclient bigint
);


--
-- Name: commandeclient_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.commandeclient_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: commandefournisseur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.commandefournisseur (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    code character varying(255),
    datecommande timestamp without time zone,
    etatcommande character varying(255),
    identreprise bigint,
    idfournisseur bigint
);


--
-- Name: commandefournisseur_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.commandefournisseur_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_order (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    code character varying(255) NOT NULL,
    customer_id bigint NOT NULL,
    order_date timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    site_id bigint NOT NULL,
    CONSTRAINT customer_order_status_check CHECK (((status)::text = ANY ((ARRAY['BROUILLON'::character varying, 'VALIDEE'::character varying, 'RESERVEE'::character varying, 'PREPAREE'::character varying, 'EXPEDIEE'::character varying, 'LIVREE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: customer_order_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_order_line (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    prix_unitaire numeric(38,2) NOT NULL,
    quantite numeric(38,2) NOT NULL,
    article_id bigint NOT NULL,
    customer_order_id bigint NOT NULL
);


--
-- Name: customer_order_line_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_order_line_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_order_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_order_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: entreprise; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.entreprise (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    adresse1 character varying(255),
    adresse2 character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    ville character varying(255),
    codefiscal character varying(255),
    description character varying(255),
    email character varying(255),
    nom character varying(255),
    numtel character varying(255),
    photo character varying(255),
    siteweb character varying(255)
);


--
-- Name: entreprise_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.entreprise_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fournisseur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fournisseur (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    adresse1 character varying(255),
    adresse2 character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    ville character varying(255),
    identreprise bigint,
    mail character varying(255),
    nom character varying(255),
    num_tel character varying(255),
    photo character varying(255),
    prenom character varying(255)
);


--
-- Name: fournisseur_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fournisseur_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignecommandeclient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignecommandeclient (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    identreprise bigint,
    prixunitaire numeric(38,2),
    quantite numeric(38,2),
    idarticle bigint,
    idcommandeclient bigint
);


--
-- Name: lignecommandeclient_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lignecommandeclient_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignecommandefournisseur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignecommandefournisseur (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    identreprise bigint,
    prixunitaire numeric(38,2),
    quantite numeric(38,2),
    idarticle bigint,
    idcommandefournisseur bigint
);


--
-- Name: lignecommandefournisseur_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lignecommandefournisseur_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lignevente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lignevente (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    identreprise bigint,
    prixunitaire numeric(38,2),
    quantite numeric(38,2),
    idarticle bigint,
    idvente bigint
);


--
-- Name: lignevente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lignevente_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mvtstk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mvtstk (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    datemvt timestamp without time zone,
    identreprise bigint,
    quantite numeric(38,2),
    sourcemvt character varying(255),
    typemvt character varying(255),
    idarticle bigint
);


--
-- Name: mvtstk_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mvtstk_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: organization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    active boolean NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);


--
-- Name: organization_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.organization_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permission (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    code character varying(255) NOT NULL,
    description character varying(255)
);


--
-- Name: permission_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permission_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    code character varying(255) NOT NULL,
    order_date timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    supplier_id bigint NOT NULL,
    site_id bigint NOT NULL,
    CONSTRAINT purchase_order_status_check CHECK (((status)::text = ANY ((ARRAY['BROUILLON'::character varying, 'VALIDEE'::character varying, 'RECUE'::character varying, 'ANNULEE'::character varying])::text[])))
);


--
-- Name: purchase_order_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.purchase_order_line (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    prix_unitaire numeric(38,2) NOT NULL,
    quantite_commandee numeric(38,2) NOT NULL,
    quantite_recue numeric(38,2) NOT NULL,
    article_id bigint NOT NULL,
    purchase_order_id bigint NOT NULL
);


--
-- Name: purchase_order_line_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_line_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.purchase_order_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);


--
-- Name: role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: role_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.role_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    rolename character varying(255),
    idutilisateur bigint
);


--
-- Name: roles_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    code character varying(255) NOT NULL,
    comment character varying(255),
    sale_date timestamp(6) with time zone NOT NULL,
    site_id bigint NOT NULL
);


--
-- Name: sale_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_line (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    prix_unitaire numeric(38,2) NOT NULL,
    quantite numeric(38,2) NOT NULL,
    article_id bigint NOT NULL,
    sale_id bigint NOT NULL
);


--
-- Name: sale_line_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_line_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: site; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.site (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    active boolean NOT NULL,
    adresse1 character varying(255),
    adresse2 character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    ville character varying(255),
    code character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    city_id bigint NOT NULL,
    CONSTRAINT site_type_check CHECK (((type)::text = ANY ((ARRAY['BOUTIQUE'::character varying, 'AGENCE'::character varying, 'ENTREPOT'::character varying])::text[])))
);


--
-- Name: site_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.site_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    quantite_physique numeric(38,2) NOT NULL,
    quantite_reservee numeric(38,2) NOT NULL,
    seuil_alerte numeric(38,2),
    version bigint,
    article_id bigint NOT NULL,
    site_id bigint NOT NULL
);


--
-- Name: stock_movement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_movement (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    date_mvt timestamp(6) with time zone NOT NULL,
    quantite numeric(38,2) NOT NULL,
    reference character varying(255),
    source character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    user_id bigint,
    article_id bigint NOT NULL,
    site_id bigint NOT NULL,
    CONSTRAINT stock_movement_source_check CHECK (((source)::text = ANY ((ARRAY['COMMANDE_CLIENT'::character varying, 'COMMANDE_FOURNISSEUR'::character varying, 'VENTE'::character varying, 'TRANSFERT'::character varying, 'CORRECTION'::character varying, 'RESERVATION'::character varying])::text[]))),
    CONSTRAINT stock_movement_type_check CHECK (((type)::text = ANY ((ARRAY['ENTREE'::character varying, 'SORTIE'::character varying, 'TRANSFERT_SORTIE'::character varying, 'TRANSFERT_ENTREE'::character varying, 'CORRECTION_POSITIVE'::character varying, 'CORRECTION_NEGATIVE'::character varying, 'RESERVATION'::character varying, 'LIBERATION_RESERVATION'::character varying])::text[])))
);


--
-- Name: stock_movement_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_movement_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_transfer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_transfer (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    approved_by_user_id bigint,
    code character varying(255) NOT NULL,
    request_date timestamp(6) with time zone NOT NULL,
    requested_by_user_id bigint NOT NULL,
    status character varying(255) NOT NULL,
    destination_site_id bigint NOT NULL,
    origin_site_id bigint NOT NULL,
    CONSTRAINT stock_transfer_status_check CHECK (((status)::text = ANY ((ARRAY['BROUILLON'::character varying, 'DEMANDE'::character varying, 'APPROUVE'::character varying, 'EN_PREPARATION'::character varying, 'EXPEDIE'::character varying, 'RECU'::character varying, 'ANNULE'::character varying])::text[])))
);


--
-- Name: stock_transfer_line; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_transfer_line (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    quantite numeric(38,2) NOT NULL,
    article_id bigint NOT NULL,
    stock_transfer_id bigint NOT NULL
);


--
-- Name: stock_transfer_line_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_transfer_line_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_transfer_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_transfer_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_role_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role_assignment (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    scope_id bigint,
    scope_type character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    CONSTRAINT user_role_assignment_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['GLOBAL'::character varying, 'ORGANIZATION'::character varying, 'CITY'::character varying, 'SITE'::character varying, 'WAREHOUSE'::character varying])::text[])))
);


--
-- Name: user_role_assignment_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_role_assignment_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: utilisateur; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.utilisateur (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    adresse1 character varying(255),
    adresse2 character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    ville character varying(255),
    datedenaissance timestamp without time zone,
    email character varying(255),
    motdepasse character varying(255),
    nom character varying(255),
    photo character varying(255),
    prenom character varying(255),
    identreprise bigint
);


--
-- Name: utilisateur_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.utilisateur_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ventes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ventes (
    id bigint NOT NULL,
    creation_date timestamp without time zone NOT NULL,
    last_modified_date timestamp without time zone,
    code character varying(255),
    commentaire character varying(255),
    datevente timestamp without time zone,
    identreprise bigint
);


--
-- Name: ventes_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ventes_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouse; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.warehouse (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    active boolean NOT NULL,
    code character varying(255) NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    site_id bigint NOT NULL
);


--
-- Name: warehouse_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouse_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: article article_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT article_pkey PRIMARY KEY (id);


--
-- Name: category category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);


--
-- Name: city city_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city
    ADD CONSTRAINT city_pkey PRIMARY KEY (id);


--
-- Name: client client_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.client
    ADD CONSTRAINT client_pkey PRIMARY KEY (id);


--
-- Name: commandeclient commandeclient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandeclient
    ADD CONSTRAINT commandeclient_pkey PRIMARY KEY (id);


--
-- Name: commandefournisseur commandefournisseur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandefournisseur
    ADD CONSTRAINT commandefournisseur_pkey PRIMARY KEY (id);


--
-- Name: customer_order_line customer_order_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order_line
    ADD CONSTRAINT customer_order_line_pkey PRIMARY KEY (id);


--
-- Name: customer_order customer_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order
    ADD CONSTRAINT customer_order_pkey PRIMARY KEY (id);


--
-- Name: entreprise entreprise_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entreprise
    ADD CONSTRAINT entreprise_pkey PRIMARY KEY (id);


--
-- Name: fournisseur fournisseur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fournisseur
    ADD CONSTRAINT fournisseur_pkey PRIMARY KEY (id);


--
-- Name: lignecommandeclient lignecommandeclient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandeclient
    ADD CONSTRAINT lignecommandeclient_pkey PRIMARY KEY (id);


--
-- Name: lignecommandefournisseur lignecommandefournisseur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandefournisseur
    ADD CONSTRAINT lignecommandefournisseur_pkey PRIMARY KEY (id);


--
-- Name: lignevente lignevente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignevente
    ADD CONSTRAINT lignevente_pkey PRIMARY KEY (id);


--
-- Name: mvtstk mvtstk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mvtstk
    ADD CONSTRAINT mvtstk_pkey PRIMARY KEY (id);


--
-- Name: organization organization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization
    ADD CONSTRAINT organization_pkey PRIMARY KEY (id);


--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_line purchase_order_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_line
    ADD CONSTRAINT purchase_order_line_pkey PRIMARY KEY (id);


--
-- Name: purchase_order purchase_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT purchase_order_pkey PRIMARY KEY (id);


--
-- Name: role_permission role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: sale_line sale_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_line
    ADD CONSTRAINT sale_line_pkey PRIMARY KEY (id);


--
-- Name: sale sale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale
    ADD CONSTRAINT sale_pkey PRIMARY KEY (id);


--
-- Name: site site_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT site_pkey PRIMARY KEY (id);


--
-- Name: stock_movement stock_movement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movement
    ADD CONSTRAINT stock_movement_pkey PRIMARY KEY (id);


--
-- Name: stock stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT stock_pkey PRIMARY KEY (id);


--
-- Name: stock_transfer_line stock_transfer_line_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer_line
    ADD CONSTRAINT stock_transfer_line_pkey PRIMARY KEY (id);


--
-- Name: stock_transfer stock_transfer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer
    ADD CONSTRAINT stock_transfer_pkey PRIMARY KEY (id);


--
-- Name: sale uk2aekpisxufvbh2pty75u6b053; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale
    ADD CONSTRAINT uk2aekpisxufvbh2pty75u6b053 UNIQUE (code);


--
-- Name: customer_order uk3qrp6h87qtgtiytruiv54mdxh; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order
    ADD CONSTRAINT uk3qrp6h87qtgtiytruiv54mdxh UNIQUE (code);


--
-- Name: warehouse uk9wk4ocyt0wv0hpffpr41aoweu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT uk9wk4ocyt0wv0hpffpr41aoweu UNIQUE (code);


--
-- Name: permission uka7ujv987la0i7a0o91ueevchc; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permission
    ADD CONSTRAINT uka7ujv987la0i7a0o91ueevchc UNIQUE (code);


--
-- Name: role ukc36say97xydpmgigg38qv5l2p; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role
    ADD CONSTRAINT ukc36say97xydpmgigg38qv5l2p UNIQUE (code);


--
-- Name: stock_transfer ukigysahcicler5iqllvk4hq6x6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer
    ADD CONSTRAINT ukigysahcicler5iqllvk4hq6x6 UNIQUE (code);


--
-- Name: warehouse ukjlc37t4s9vxopreicapjjresm; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT ukjlc37t4s9vxopreicapjjresm UNIQUE (site_id);


--
-- Name: purchase_order uklyhuui3e3rh2a6itktx3rwrpe; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT uklyhuui3e3rh2a6itktx3rwrpe UNIQUE (code);


--
-- Name: site ukngr72utqwkb6bkfb0l6v03g3e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT ukngr72utqwkb6bkfb0l6v03g3e UNIQUE (code);


--
-- Name: stock ukoj4sfc22ppjhtulifhh9a3fkw; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT ukoj4sfc22ppjhtulifhh9a3fkw UNIQUE (article_id, site_id);


--
-- Name: user_role_assignment user_role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignment
    ADD CONSTRAINT user_role_assignment_pkey PRIMARY KEY (id);


--
-- Name: utilisateur utilisateur_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT utilisateur_pkey PRIMARY KEY (id);


--
-- Name: ventes ventes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ventes
    ADD CONSTRAINT ventes_pkey PRIMARY KEY (id);


--
-- Name: warehouse warehouse_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT warehouse_pkey PRIMARY KEY (id);


--
-- Name: city fk1fc10uauhx3ccwjv2mltlqk0d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.city
    ADD CONSTRAINT fk1fc10uauhx3ccwjv2mltlqk0d FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: utilisateur fk1lqyf8cuumbj0iku4axqklfu3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utilisateur
    ADD CONSTRAINT fk1lqyf8cuumbj0iku4axqklfu3 FOREIGN KEY (identreprise) REFERENCES public.entreprise(id);


--
-- Name: purchase_order_line fk210t80fsgdi4s4g7tlg9vdgkd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_line
    ADD CONSTRAINT fk210t80fsgdi4s4g7tlg9vdgkd FOREIGN KEY (purchase_order_id) REFERENCES public.purchase_order(id);


--
-- Name: lignecommandeclient fk29ctec6walxsuc2jcixedf15s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandeclient
    ADD CONSTRAINT fk29ctec6walxsuc2jcixedf15s FOREIGN KEY (idarticle) REFERENCES public.article(id);


--
-- Name: commandeclient fk2t3ma3ko3u9hoiuqafjai8f9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandeclient
    ADD CONSTRAINT fk2t3ma3ko3u9hoiuqafjai8f9 FOREIGN KEY (idclient) REFERENCES public.client(id);


--
-- Name: stock_transfer fk4gyglujnt6ogs5nf4y8vvkqxq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer
    ADD CONSTRAINT fk4gyglujnt6ogs5nf4y8vvkqxq FOREIGN KEY (origin_site_id) REFERENCES public.site(id);


--
-- Name: purchase_order fk5l6fhkcirmu67cu1ku9nhctdu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order
    ADD CONSTRAINT fk5l6fhkcirmu67cu1ku9nhctdu FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: roles fk71xd67w89vvcotymi95xc1k59; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT fk71xd67w89vvcotymi95xc1k59 FOREIGN KEY (idutilisateur) REFERENCES public.utilisateur(id);


--
-- Name: stock fka1vlr0ani5bpui1f7s98ku0ik; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT fka1vlr0ani5bpui1f7s98ku0ik FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: role_permission fka6jx8n8xkesmjmv6jqug6bg68; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fka6jx8n8xkesmjmv6jqug6bg68 FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: commandefournisseur fkatj6buy5fvy1cuf1wsm95dgvw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.commandefournisseur
    ADD CONSTRAINT fkatj6buy5fvy1cuf1wsm95dgvw FOREIGN KEY (idfournisseur) REFERENCES public.fournisseur(id);


--
-- Name: article fkcgg5kkexxy1usb9vrbkeh7ybd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT fkcgg5kkexxy1usb9vrbkeh7ybd FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: stock_movement fkdgm1qix5qpb0g8dy51n5nu3sq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movement
    ADD CONSTRAINT fkdgm1qix5qpb0g8dy51n5nu3sq FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: stock_transfer_line fke4eqpsd3k3ycyp3x67invb4vv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer_line
    ADD CONSTRAINT fke4eqpsd3k3ycyp3x67invb4vv FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: purchase_order_line fke86m506f2wpu0grh3w13by7mu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.purchase_order_line
    ADD CONSTRAINT fke86m506f2wpu0grh3w13by7mu FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: role_permission fkf8yllw1ecvwqy3ehyxawqa1qp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permission
    ADD CONSTRAINT fkf8yllw1ecvwqy3ehyxawqa1qp FOREIGN KEY (permission_id) REFERENCES public.permission(id);


--
-- Name: customer_order_line fkg4dtkbxpnll6b3esjkcl68f50; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order_line
    ADD CONSTRAINT fkg4dtkbxpnll6b3esjkcl68f50 FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: stock fkhdhd1ttwutughmxerlblecgce; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT fkhdhd1ttwutughmxerlblecgce FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: lignecommandefournisseur fkk0l9s5k2stvo3i2s71jrdmlal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandefournisseur
    ADD CONSTRAINT fkk0l9s5k2stvo3i2s71jrdmlal FOREIGN KEY (idcommandefournisseur) REFERENCES public.commandefournisseur(id);


--
-- Name: stock_movement fkm9949ghvax050lfrshfi1v8uj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movement
    ADD CONSTRAINT fkm9949ghvax050lfrshfi1v8uj FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: user_role_assignment fkn4tude85emlq2g3nijn4fvrut; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_assignment
    ADD CONSTRAINT fkn4tude85emlq2g3nijn4fvrut FOREIGN KEY (role_id) REFERENCES public.role(id);


--
-- Name: stock_transfer fknhqv9om7pgt5tgrydn0a28g8g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer
    ADD CONSTRAINT fknhqv9om7pgt5tgrydn0a28g8g FOREIGN KEY (destination_site_id) REFERENCES public.site(id);


--
-- Name: article fknwo5h4bghmo8jk7jpavwtwi6i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.article
    ADD CONSTRAINT fknwo5h4bghmo8jk7jpavwtwi6i FOREIGN KEY (idcategory) REFERENCES public.category(id);


--
-- Name: warehouse fko02cnjatyu0wrs100p81uw6xw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.warehouse
    ADD CONSTRAINT fko02cnjatyu0wrs100p81uw6xw FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: category fko7b50yjmurpmcfg8hneap1u8f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT fko7b50yjmurpmcfg8hneap1u8f FOREIGN KEY (organization_id) REFERENCES public.organization(id);


--
-- Name: lignevente fkobnh6mrbkqliny3g58mfpheqj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignevente
    ADD CONSTRAINT fkobnh6mrbkqliny3g58mfpheqj FOREIGN KEY (idarticle) REFERENCES public.article(id);


--
-- Name: customer_order_line fkoj0mly1bn95etvnf5wxd9b8o0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order_line
    ADD CONSTRAINT fkoj0mly1bn95etvnf5wxd9b8o0 FOREIGN KEY (customer_order_id) REFERENCES public.customer_order(id);


--
-- Name: sale fkojiamf7m9v6ajmkf8j2t7r1jg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale
    ADD CONSTRAINT fkojiamf7m9v6ajmkf8j2t7r1jg FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: customer_order fkosvb5sqsih9c4yu7ifwc1tbt1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_order
    ADD CONSTRAINT fkosvb5sqsih9c4yu7ifwc1tbt1 FOREIGN KEY (site_id) REFERENCES public.site(id);


--
-- Name: mvtstk fkpt75sr5je032y1ppv8rw9nqh2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mvtstk
    ADD CONSTRAINT fkpt75sr5je032y1ppv8rw9nqh2 FOREIGN KEY (idarticle) REFERENCES public.article(id);


--
-- Name: sale_line fkpyj3djxgokihx13gcgw6j9y7d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_line
    ADD CONSTRAINT fkpyj3djxgokihx13gcgw6j9y7d FOREIGN KEY (sale_id) REFERENCES public.sale(id);


--
-- Name: stock_transfer_line fkr9pw1jbxji6p5xb63mfeytxi8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_transfer_line
    ADD CONSTRAINT fkr9pw1jbxji6p5xb63mfeytxi8 FOREIGN KEY (stock_transfer_id) REFERENCES public.stock_transfer(id);


--
-- Name: site fkroako7fxfvlerf7y01te8axa; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.site
    ADD CONSTRAINT fkroako7fxfvlerf7y01te8axa FOREIGN KEY (city_id) REFERENCES public.city(id);


--
-- Name: lignecommandefournisseur fks25oxp23762ei310opvngg5v1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandefournisseur
    ADD CONSTRAINT fks25oxp23762ei310opvngg5v1 FOREIGN KEY (idarticle) REFERENCES public.article(id);


--
-- Name: sale_line fksvi4sysiv2590967q2alelrm0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_line
    ADD CONSTRAINT fksvi4sysiv2590967q2alelrm0 FOREIGN KEY (article_id) REFERENCES public.article(id);


--
-- Name: lignevente fktdh351xwh8t4r8omustgvg2y0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignevente
    ADD CONSTRAINT fktdh351xwh8t4r8omustgvg2y0 FOREIGN KEY (idvente) REFERENCES public.ventes(id);


--
-- Name: lignecommandeclient fkthsj2spmxb6ygsyj39osvie55; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lignecommandeclient
    ADD CONSTRAINT fkthsj2spmxb6ygsyj39osvie55 FOREIGN KEY (idcommandeclient) REFERENCES public.commandeclient(id);


--
--


