package com.kfokam48.gestiondestock.inventory.application;

import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.inventory.application.dto.StockMovementDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import java.math.BigDecimal;
import java.util.List;

/**
 * Point d'entree unique du module inventory pour les autres modules (sales, purchasing,
 * transfers a partir des Phases 7-9). Aucun module consommateur ne doit passer par
 * {@link com.kfokam48.gestiondestock.inventory.infrastructure.persistence.StockRepository}
 * directement (regle section 23 du prompt maitre).
 */
public interface InventoryFacade {

  StockDto getStock(Long articleId, Long siteId);

  /** Stock physique total d'un article, tous sites confondus (Phase 22 : legacy stockReelArticle n'a pas de notion de site). */
  BigDecimal getTotalStockForArticle(Long articleId);

  /** Tout le stock d'un site, tous articles confondus (reporting). */
  List<StockDto> findStockBySite(Long siteId);

  /** Tout le stock, tous sites confondus (reporting a perimetre GLOBAL uniquement). */
  List<StockDto> findAllStock();

  /** Stock dont le disponible est descendu au niveau ou sous son seuil d'alerte (section 49). */
  List<StockDto> findLowStock();

  boolean isAvailable(Long articleId, Long siteId, BigDecimal quantity);

  List<StockMovementDto> findMovements(Long articleId, Long siteId);

  StockMovementDto receive(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId);

  StockMovementDto issue(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId);

  StockMovementDto reserve(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId);

  StockMovementDto releaseReservation(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId);

  StockMovementDto correct(Long articleId, Long siteId, BigDecimal delta, String reference, Long userId);

  /**
   * Sortie de stock au site d'origine d'un transfert (StockMovementType.TRANSFERT_SORTIE) -
   * distincte de {@link #issue} pour que le mouvement soit trace avec le bon type (section 15).
   */
  StockMovementDto transferOut(Long articleId, Long originSiteId, BigDecimal quantity, String reference, Long userId);

  /**
   * Entree de stock au site de destination d'un transfert (StockMovementType.TRANSFERT_ENTREE).
   */
  StockMovementDto transferIn(Long articleId, Long destinationSiteId, BigDecimal quantity, String reference, Long userId);

}
