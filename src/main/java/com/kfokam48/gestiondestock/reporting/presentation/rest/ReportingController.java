package com.kfokam48.gestiondestock.reporting.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.reporting.application.ReportingService;
import com.kfokam48.gestiondestock.reporting.application.dto.SalesSummaryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "reporting")
@RestController
public class ReportingController {

  private ReportingService reportingService;

  @Autowired
  public ReportingController(ReportingService reportingService) {
    this.reportingService = reportingService;
  }

  // userId resolu depuis le principal authentifie, jamais depuis un parametre fourni par
  // l'appelant : un "userId" en @RequestParam aurait permis a n'importe quel appelant de se
  // faire passer pour n'importe quel utilisateur pour les besoins de AuthorizationService.

  @GetMapping(value = APP_ROOT + "/reporting/stock/site/{idSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockDto> getStockForSite(@PathVariable("idSite") Long siteId, @AuthenticationPrincipal ExtendedUser principal) {
    return reportingService.getStockForSite(principal.getIdUtilisateur(), siteId, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/reporting/stock/global", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockDto> getGlobalStockSummary(@AuthenticationPrincipal ExtendedUser principal) {
    return reportingService.getGlobalStockSummary(principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/reporting/stock/low", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockDto> getLowStockReport(@AuthenticationPrincipal ExtendedUser principal) {
    return reportingService.getLowStockReport(principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/reporting/sales/site/{idSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SalesSummaryDto getSalesSummary(@PathVariable("idSite") Long siteId, @AuthenticationPrincipal ExtendedUser principal,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return reportingService.getSalesSummary(principal.getIdUtilisateur(), siteId, from, to, principal.getOrganizationId());
  }
}
