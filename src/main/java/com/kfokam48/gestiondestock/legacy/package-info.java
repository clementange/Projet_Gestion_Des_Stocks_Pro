/**
 * Phase 4e : donnees ANTERIEURES a la migration, et elles seules.
 *
 * <p>Les tables plates {@code lignevente}, {@code lignecommandeclient} et
 * {@code lignecommandefournisseur} contiennent l'historique d'avant la migration, que les tables
 * des modules ne contiennent pas : les endpoints {@code /articles/historique/**} fusionnent les
 * deux sources. Supprimer ces entites ferait disparaitre des donnees de la reponse HTTP — une
 * regression fonctionnelle, pas un nettoyage. Elles sont donc rassemblees ici, sous un nom
 * explicite, au lieu de rester dispersees dans les paquets plats {@code model/}, {@code dto/} et
 * {@code repository/} (desormais supprimes).
 *
 * <p>Ce module ne doit JAMAIS grossir : aucune fonctionnalite neuve, aucune nouvelle entite. Il
 * depend de {@code catalog} ({@code Article}, {@code ArticleDto}) et fournit
 * {@code catalog.domain.port.ArticleUsagePort} — l'inversion qui evite le cycle
 * {@code catalog <-> legacy}.
 *
 * <p>Voir docs/api-migration-map.md §5 et docs/phase-4e-report.md.
 */
package com.kfokam48.gestiondestock.legacy;
