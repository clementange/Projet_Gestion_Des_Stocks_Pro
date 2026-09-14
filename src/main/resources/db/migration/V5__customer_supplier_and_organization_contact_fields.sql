-- Phase 16 de la migration monolithe modulaire (voir docs/ARCHITECTURE.md, plan Phase 16-23) :
-- fondations pour re-backer les endpoints legacy (Client/Fournisseur/Ventes/CommandeClient/
-- CommandeFournisseur/MvtStk) par les modules sales/purchasing/organization/inventory, sans
-- casser leur contrat HTTP. Aucune donnee legacy n'est modifiee ici, uniquement additif.

CREATE TABLE public.customer (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    nom character varying(255),
    prenom character varying(255),
    adresse1 character varying(255),
    adresse2 character varying(255),
    ville character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    photo character varying(255),
    mail character varying(255),
    num_tel character varying(255),
    organization_id bigint,
    CONSTRAINT customer_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE public.customer_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE public.supplier (
    id bigint NOT NULL,
    creation_date timestamp(6) with time zone NOT NULL,
    last_modified_date timestamp(6) with time zone,
    nom character varying(255),
    prenom character varying(255),
    adresse1 character varying(255),
    adresse2 character varying(255),
    ville character varying(255),
    codepostale character varying(255),
    pays character varying(255),
    photo character varying(255),
    mail character varying(255),
    num_tel character varying(255),
    organization_id bigint,
    CONSTRAINT supplier_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE public.supplier_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE public.organization
    ADD COLUMN email character varying(255),
    ADD COLUMN phone character varying(255),
    ADD COLUMN website character varying(255),
    ADD COLUMN tax_code character varying(255),
    ADD COLUMN photo character varying(255),
    ADD COLUMN adresse1 character varying(255),
    ADD COLUMN adresse2 character varying(255),
    ADD COLUMN ville character varying(255),
    ADD COLUMN codepostale character varying(255),
    ADD COLUMN pays character varying(255);

ALTER TABLE public.entreprise
    ADD COLUMN organization_id bigint;

ALTER TABLE ONLY public.entreprise
    ADD CONSTRAINT fk_entreprise_organization FOREIGN KEY (organization_id) REFERENCES public.organization(id);
