package com.kfokam48.gestiondestock.media.application.dto;

/**
 * Corps de requete partage par les endpoints {@code POST /{ressource}/{id}/photo} de chaque
 * module (catalog, sales/legacy, purchasing/legacy, tenant, identity) - evite de dupliquer ce
 * record 5 fois. Voir docs/phase-4c-report.md.
 */
public record PhotoUrlRequest(String url) {
}
