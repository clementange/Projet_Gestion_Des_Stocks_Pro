package com.kfokam48.gestiondestock.legacy;

import com.kfokam48.gestiondestock.catalog.domain.port.ArticleUsagePort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Phase 4e : satisfait {@link ArticleUsagePort} declare par {@code catalog}, en interrogeant les
 * trois tables plates historiques. Les libelles renvoyes sont EXACTEMENT ceux que
 * {@code ArticleServiceImpl.delete} assemblait avant l'inversion de dependance, pour que le
 * message d'erreur HTTP reste identique au caractere.
 */
@Component
public class LegacyArticleUsageAdapter implements ArticleUsagePort {

  private final LigneCommandeClientRepository ligneCommandeClientRepository;
  private final LigneCommandeFournisseurRepository ligneCommandeFournisseurRepository;
  private final LigneVenteRepository ligneVenteRepository;

  public LegacyArticleUsageAdapter(LigneCommandeClientRepository ligneCommandeClientRepository,
      LigneCommandeFournisseurRepository ligneCommandeFournisseurRepository,
      LigneVenteRepository ligneVenteRepository) {
    this.ligneCommandeClientRepository = ligneCommandeClientRepository;
    this.ligneCommandeFournisseurRepository = ligneCommandeFournisseurRepository;
    this.ligneVenteRepository = ligneVenteRepository;
  }

  // Ordre de verification conserve (commandes client, puis fournisseur, puis ventes) : il decide
  // du message renvoye quand un article est reference dans plusieurs historiques.
  @Override
  public Optional<String> findBlockingHistoricalUsage(Long articleId) {
    if (!ligneCommandeClientRepository.findAllByArticleId(articleId).isEmpty()) {
      return Optional.of("commandes client");
    }
    if (!ligneCommandeFournisseurRepository.findAllByArticleId(articleId).isEmpty()) {
      return Optional.of("commandes fournisseur");
    }
    if (!ligneVenteRepository.findAllByArticleId(articleId).isEmpty()) {
      return Optional.of("ventes");
    }
    return Optional.empty();
  }
}
