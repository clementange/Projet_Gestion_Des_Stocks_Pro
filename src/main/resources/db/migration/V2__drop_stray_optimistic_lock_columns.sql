-- V2__drop_stray_optimistic_lock_columns.sql
--
-- Phase 13 (nettoyage) : la baseline V1 excluait deja ces colonnes/sequence du schema attendu
-- par Hibernate (elles ne sont mappees par aucune entite), mais V1 n'a jamais ete rejouee
-- litteralement sur la base de dev (baseline-on-migrate) : les artefacts physiques restaient.
-- Cette migration les retire reellement de toute base qui a connu ces phases.
--
-- Origine : un essai de verrouillage optimiste global (@Version sur AbstractEntity) fait puis
-- annule en Phase 2 avait laisse une colonne "version" orpheline sur 14 tables legacy. Seul
-- Stock (agregat inventory, Phase 6) porte legitimement un numero de version : non touchee ici.
-- "hibernate_sequence" est une sequence generique orpheline depuis le passage a des sequences
-- dediees par entite sous Hibernate 6 (Phase 1).

ALTER TABLE public.article DROP COLUMN IF EXISTS version;
ALTER TABLE public.category DROP COLUMN IF EXISTS version;
ALTER TABLE public.client DROP COLUMN IF EXISTS version;
ALTER TABLE public.commandeclient DROP COLUMN IF EXISTS version;
ALTER TABLE public.commandefournisseur DROP COLUMN IF EXISTS version;
ALTER TABLE public.entreprise DROP COLUMN IF EXISTS version;
ALTER TABLE public.fournisseur DROP COLUMN IF EXISTS version;
ALTER TABLE public.lignecommandeclient DROP COLUMN IF EXISTS version;
ALTER TABLE public.lignecommandefournisseur DROP COLUMN IF EXISTS version;
ALTER TABLE public.lignevente DROP COLUMN IF EXISTS version;
ALTER TABLE public.mvtstk DROP COLUMN IF EXISTS version;
ALTER TABLE public.roles DROP COLUMN IF EXISTS version;
ALTER TABLE public.utilisateur DROP COLUMN IF EXISTS version;
ALTER TABLE public.ventes DROP COLUMN IF EXISTS version;

DROP SEQUENCE IF EXISTS public.hibernate_sequence;
