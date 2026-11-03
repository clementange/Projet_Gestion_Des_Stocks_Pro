package com.kfokam48.gestiondestock.catalog.domain.port;

import java.util.Optional;

/**
 * Phase 4e : port d'inversion de dependance, possede par {@code catalog}.
 *
 * <p>{@code ArticleServiceImpl.delete} doit refuser de supprimer un article encore reference par
 * une ligne de vente ou de commande ANTERIEURE a la migration — ces lignes vivent dans les tables
 * plates historiques, servies par le module {@code legacy}. Appeler directement les repositories
 * de {@code legacy} depuis {@code catalog} formerait un cycle : {@code legacy} depend deja de
 * {@code catalog} (ses entites portent un {@code @ManyToOne Article} et ses DTO exposent un
 * {@code ArticleDto}). {@code catalog} declare donc le besoin, {@code legacy} le satisfait.
 *
 * <p>Voir docs/api-migration-map.md §5 et docs/phase-4e-report.md.
 */
public interface ArticleUsagePort {

  /**
   * Libelle de l'usage historique qui interdit la suppression (« commandes client »,
   * « commandes fournisseur », « ventes »), ou {@link Optional#empty()} si l'article n'est
   * reference par aucune ligne historique.
   */
  Optional<String> findBlockingHistoricalUsage(Long articleId);
}
