package com.kfokam48.gestiondestock.inventory.application.impl;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.inventory.application.dto.StockMovementDto;
import com.kfokam48.gestiondestock.inventory.domain.model.Stock;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovement;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementType;
import com.kfokam48.gestiondestock.inventory.infrastructure.persistence.StockMovementRepository;
import com.kfokam48.gestiondestock.inventory.infrastructure.persistence.StockRepository;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class InventoryFacadeImpl implements InventoryFacade {

  private StockRepository stockRepository;
  private StockMovementRepository stockMovementRepository;

  @Autowired
  public InventoryFacadeImpl(StockRepository stockRepository, StockMovementRepository stockMovementRepository) {
    this.stockRepository = stockRepository;
    this.stockMovementRepository = stockMovementRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public StockDto getStock(Long articleId, Long siteId) {
    return stockRepository.findByArticleIdAndSiteId(articleId, siteId)
        .map(StockDto::fromEntity)
        .orElseGet(() -> StockDto.builder()
            .quantitePhysique(BigDecimal.ZERO)
            .quantiteReservee(BigDecimal.ZERO)
            .quantiteDisponible(BigDecimal.ZERO)
            .build());
  }

  @Override
  @Transactional(readOnly = true)
  public BigDecimal getTotalStockForArticle(Long articleId) {
    return stockRepository.findAllByArticleId(articleId).stream()
        .map(Stock::getQuantitePhysique)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockDto> findStockBySite(Long siteId) {
    return stockRepository.findAllBySiteId(siteId).stream()
        .map(StockDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockDto> findAllStock() {
    return stockRepository.findAll().stream()
        .map(StockDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockDto> findLowStock() {
    return stockRepository.findAll().stream()
        .filter(stock -> stock.getSeuilAlerte() != null
            && stock.getQuantiteDisponible().compareTo(stock.getSeuilAlerte()) <= 0)
        .map(StockDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isAvailable(Long articleId, Long siteId, BigDecimal quantity) {
    return stockRepository.findByArticleIdAndSiteId(articleId, siteId)
        .map(stock -> stock.getQuantiteDisponible().compareTo(quantity) >= 0)
        .orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockMovementDto> findMovements(Long articleId, Long siteId) {
    return stockMovementRepository.findAllByArticleIdAndSiteId(articleId, siteId).stream()
        .map(StockMovementDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public StockMovementDto receive(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, siteId);
    stock.receive(quantity);
    saveStock(stock);
    return recordMovement(articleId, siteId, quantity, StockMovementType.ENTREE, source, reference, userId);
  }

  @Override
  public StockMovementDto issue(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, siteId);
    stock.issue(quantity);
    saveStock(stock);
    return recordMovement(articleId, siteId, quantity.negate(), StockMovementType.SORTIE, source, reference, userId);
  }

  @Override
  public StockMovementDto reserve(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, siteId);
    stock.reserve(quantity);
    saveStock(stock);
    return recordMovement(articleId, siteId, quantity, StockMovementType.RESERVATION, source, reference, userId);
  }

  @Override
  public StockMovementDto releaseReservation(Long articleId, Long siteId, BigDecimal quantity, StockMovementSource source, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, siteId);
    stock.releaseReservation(quantity);
    saveStock(stock);
    return recordMovement(articleId, siteId, quantity, StockMovementType.LIBERATION_RESERVATION, source, reference, userId);
  }

  @Override
  public StockMovementDto correct(Long articleId, Long siteId, BigDecimal delta, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, siteId);
    stock.correct(delta);
    saveStock(stock);
    StockMovementType type = delta.compareTo(BigDecimal.ZERO) > 0 ? StockMovementType.CORRECTION_POSITIVE : StockMovementType.CORRECTION_NEGATIVE;
    return recordMovement(articleId, siteId, delta, type, StockMovementSource.CORRECTION, reference, userId);
  }

  @Override
  public StockMovementDto transferOut(Long articleId, Long originSiteId, BigDecimal quantity, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, originSiteId);
    stock.issue(quantity);
    saveStock(stock);
    return recordMovement(articleId, originSiteId, quantity.negate(), StockMovementType.TRANSFERT_SORTIE,
        StockMovementSource.TRANSFERT, reference, userId);
  }

  @Override
  public StockMovementDto transferIn(Long articleId, Long destinationSiteId, BigDecimal quantity, String reference, Long userId) {
    Stock stock = getOrCreateStock(articleId, destinationSiteId);
    stock.receive(quantity);
    saveStock(stock);
    return recordMovement(articleId, destinationSiteId, quantity, StockMovementType.TRANSFERT_ENTREE,
        StockMovementSource.TRANSFERT, reference, userId);
  }

  private Stock getOrCreateStock(Long articleId, Long siteId) {
    return stockRepository.findByArticleIdAndSiteId(articleId, siteId)
        .orElseGet(() -> {
          Stock stock = new Stock();
          stock.setArticle(articleReference(articleId));
          stock.setSite(siteReference(siteId));
          stock.setQuantitePhysique(BigDecimal.ZERO);
          stock.setQuantiteReservee(BigDecimal.ZERO);
          return stock;
        });
  }

  /**
   * Une simple reference (ID seul) suffit pour une colonne de jointure @ManyToOne : Hibernate n'a
   * besoin que de l'ID pour ecrire la FK, pas d'une entite geree par la session.
   */
  private Article articleReference(Long id) {
    Article article = new Article();
    article.setId(id);
    return article;
  }

  private Site siteReference(Long id) {
    Site site = new Site();
    site.setId(id);
    return site;
  }

  private void saveStock(Stock stock) {
    try {
      stockRepository.saveAndFlush(stock);
    } catch (ObjectOptimisticLockingFailureException e) {
      log.warn("Modification concurrente detectee sur le stock article={} site={}",
          stock.getArticle().getId(), stock.getSite().getId());
      throw new InvalidOperationException(
          "Le stock a ete modifie entre-temps par une autre operation, veuillez reessayer",
          ErrorCodes.STOCK_CONCURRENT_MODIFICATION);
    }
  }

  private StockMovementDto recordMovement(Long articleId, Long siteId, BigDecimal signedQuantity, StockMovementType type,
      StockMovementSource source, String reference, Long userId) {
    StockMovement movement = new StockMovement();
    movement.setArticle(articleReference(articleId));
    movement.setSite(siteReference(siteId));
    movement.setDateMvt(Instant.now());
    movement.setQuantite(signedQuantity);
    movement.setType(type);
    movement.setSource(source);
    movement.setReference(reference);
    movement.setUserId(userId);
    return StockMovementDto.fromEntity(stockMovementRepository.save(movement));
  }
}
