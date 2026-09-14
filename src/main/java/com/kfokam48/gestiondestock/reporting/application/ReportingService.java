package com.kfokam48.gestiondestock.reporting.application;

import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.reporting.application.dto.SalesSummaryDto;
import java.time.Instant;
import java.util.List;

/**
 * Lecture seule, cross-module (inventory, sales, organization), filtree par le perimetre RBAC de
 * l'appelant (section 50 : "un responsable d'entrepot ne doit pas pouvoir voir les statistiques
 * globales s'il n'en a pas la permission"). Chaque methode verifie la permission REPORTING_VIEW
 * via AuthorizationService avant de renvoyer quoi que ce soit.
 */
public interface ReportingService {

  List<StockDto> getStockForSite(Long userId, Long siteId);

  /** Reserve aux detenteurs d'une affectation GLOBAL avec la permission REPORTING_VIEW. */
  List<StockDto> getGlobalStockSummary(Long userId);

  /**
   * Articles sous leur seuil d'alerte, filtre aux sites accessibles par l'appelant (tous les
   * sites pour un perimetre GLOBAL, uniquement les sites explicitement autorises sinon).
   */
  List<StockDto> getLowStockReport(Long userId);

  SalesSummaryDto getSalesSummary(Long userId, Long siteId, Instant from, Instant to);

}
