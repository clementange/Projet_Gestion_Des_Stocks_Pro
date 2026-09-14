package com.kfokam48.gestiondestock.reporting.application.impl;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.reporting.application.ReportingService;
import com.kfokam48.gestiondestock.reporting.application.dto.SalesSummaryDto;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReportingServiceImpl implements ReportingService {

  private static final String REPORTING_VIEW = "REPORTING_VIEW";

  private AuthorizationService authorizationService;
  private InventoryFacade inventoryFacade;
  private SiteService siteService;
  private SaleService saleService;

  @Autowired
  public ReportingServiceImpl(AuthorizationService authorizationService, InventoryFacade inventoryFacade,
      SiteService siteService, SaleService saleService) {
    this.authorizationService = authorizationService;
    this.inventoryFacade = inventoryFacade;
    this.siteService = siteService;
    this.saleService = saleService;
  }

  @Override
  public List<StockDto> getStockForSite(Long userId, Long siteId) {
    requireSiteAccess(userId, siteId);
    return inventoryFacade.findStockBySite(siteId);
  }

  @Override
  public List<StockDto> getGlobalStockSummary(Long userId) {
    if (!authorizationService.hasPermission(userId, REPORTING_VIEW, ScopeType.GLOBAL, null)) {
      log.warn("User {} tried to access the global stock summary without GLOBAL REPORTING_VIEW", userId);
      throw new InvalidOperationException(
          "Seul un perimetre GLOBAL avec la permission REPORTING_VIEW peut consulter les statistiques globales",
          ErrorCodes.REPORTING_ACCESS_DENIED);
    }
    return inventoryFacade.findAllStock();
  }

  @Override
  public List<StockDto> getLowStockReport(Long userId) {
    return inventoryFacade.findLowStock().stream()
        .filter(stock -> canAccessSite(userId, stock.getSite().getId()))
        .collect(Collectors.toList());
  }

  @Override
  public SalesSummaryDto getSalesSummary(Long userId, Long siteId, Instant from, Instant to) {
    requireSiteAccess(userId, siteId);

    SiteDto site = siteService.findById(siteId);
    List<SaleDto> sales = saleService.findAllBySiteAndPeriod(siteId, from, to);

    BigDecimal totalQuantity = BigDecimal.ZERO;
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (SaleDto sale : sales) {
      for (SaleLineDto line : saleService.findLines(sale.getId())) {
        totalQuantity = totalQuantity.add(line.getQuantite());
        totalAmount = totalAmount.add(line.getQuantite().multiply(line.getPrixUnitaire()));
      }
    }

    return SalesSummaryDto.builder()
        .site(site)
        .from(from)
        .to(to)
        .saleCount(sales.size())
        .totalQuantity(totalQuantity)
        .totalAmount(totalAmount)
        .build();
  }

  private void requireSiteAccess(Long userId, Long siteId) {
    if (!canAccessSite(userId, siteId)) {
      log.warn("User {} tried to access reports for site {} without REPORTING_VIEW on that scope", userId, siteId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission de consulter les statistiques de ce site",
          ErrorCodes.REPORTING_ACCESS_DENIED);
    }
  }

  /**
   * Une affectation GLOBAL satisfait toujours cette verification (AuthorizationServiceImpl).
   * Par convention, une affectation WAREHOUSE porte le meme ID que le Site qu'elle gere (Site et
   * Warehouse sont en 1:1 depuis la Phase 3) : cela evite une resolution Warehouse -> Site
   * inter-module non justifiee tant qu'aucun autre besoin ne l'exige (meme logique que le
   * deferral documente en Phase 4).
   */
  private boolean canAccessSite(Long userId, Long siteId) {
    return authorizationService.hasPermission(userId, REPORTING_VIEW, ScopeType.SITE, siteId)
        || authorizationService.hasPermission(userId, REPORTING_VIEW, ScopeType.WAREHOUSE, siteId);
  }
}
