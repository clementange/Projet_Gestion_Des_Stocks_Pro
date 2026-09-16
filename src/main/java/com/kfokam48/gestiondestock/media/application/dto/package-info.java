/**
 * Surface publique (DTO) du module {@code media} : {@code PhotoUrlRequest} est legitimement
 * reference par catalog, sales, purchasing, tenant et identity (chacun expose un endpoint
 * {@code POST /{ressource}/{id}/photo} qui accepte ce corps de requete) - voir
 * docs/phase-4c-report.md.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.kfokam48.gestiondestock.media.application.dto;
