package com.kfokam48.gestiondestock.inventory.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.inventory.application.dto.StockMovementDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 5b-2d : receive/issue/correct (ecriture) sont desormais scopes a l'organisation de
 * l'appelant, meme motif que getStock/findMovements en 5b-2b, plus une permission dediee par
 * action (STOCK_RECEIVE/STOCK_ISSUE/STOCK_CORRECT) puisqu'une correction de stock directe mute
 * la quantite physique immediatement, sans workflow d'approbation - au moins aussi sensible que
 * PurchaseOrder.receiveLine/StockTransfer.ship/receive, deja gates de la meme facon - voir
 * docs/phase-5b2d-report.md.
 */
@Tag(name = "inventory")
@RestController
@Slf4j
public class InventoryController {

  private static final String STOCK_RECEIVE = "STOCK_RECEIVE";
  private static final String STOCK_ISSUE = "STOCK_ISSUE";
  private static final String STOCK_CORRECT = "STOCK_CORRECT";

  private InventoryFacade inventoryFacade;
  private SiteService siteService;
  private AuthorizationService authorizationService;

  @Autowired
  public InventoryController(InventoryFacade inventoryFacade, SiteService siteService,
      AuthorizationService authorizationService) {
    this.inventoryFacade = inventoryFacade;
    this.siteService = siteService;
    this.authorizationService = authorizationService;
  }

  @GetMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockDto getStock(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @AuthenticationPrincipal ExtendedUser principal) {
    siteService.findById(idSite, principal.getOrganizationId());
    return inventoryFacade.getStock(idArticle, idSite);
  }

  @GetMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/mouvements", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockMovementDto> findMovements(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @AuthenticationPrincipal ExtendedUser principal) {
    siteService.findById(idSite, principal.getOrganizationId());
    return inventoryFacade.findMovements(idArticle, idSite);
  }

  /**
   * Phase 5b-2d : l'appartenance du site a l'organisation de l'appelant est verifiee EN PREMIER
   * (siteService.findById(idSite, organizationId), meme appel que sur les lectures ci-dessus),
   * puis la permission dediee EN SECOND - garde-fou independant, non contournable par la logique
   * RBAC elle-meme, meme ordre que requireSiteInOrganization avant hasPermission (5b-2b) et le
   * garde-fou d'appartenance de UserRoleAssignment (5b-2c).
   */
  private void requirePermissionOnOwnSite(Long idSite, ExtendedUser principal, String permissionCode, ErrorCodes deniedCode) {
    siteService.findById(idSite, principal.getOrganizationId());
    if (!authorizationService.hasPermission(principal.getIdUtilisateur(), permissionCode, ScopeType.SITE, idSite, principal.getOrganizationId())) {
      log.warn("User {} tried to {} on site {} without permission", principal.getIdUtilisateur(), permissionCode, idSite);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action sur ce site",
          deniedCode);
    }
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/entree", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto receive(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal quantite, @RequestParam(required = false) String reference,
      @AuthenticationPrincipal ExtendedUser principal) {
    requirePermissionOnOwnSite(idSite, principal, STOCK_RECEIVE, ErrorCodes.STOCK_ACCESS_DENIED);
    return inventoryFacade.receive(idArticle, idSite, quantite, StockMovementSource.CORRECTION, reference, principal.getIdUtilisateur());
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/sortie", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto issue(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal quantite, @RequestParam(required = false) String reference,
      @AuthenticationPrincipal ExtendedUser principal) {
    requirePermissionOnOwnSite(idSite, principal, STOCK_ISSUE, ErrorCodes.STOCK_ACCESS_DENIED);
    return inventoryFacade.issue(idArticle, idSite, quantite, StockMovementSource.CORRECTION, reference, principal.getIdUtilisateur());
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/correction", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto correct(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal delta, @RequestParam(required = false) String reference,
      @AuthenticationPrincipal ExtendedUser principal) {
    requirePermissionOnOwnSite(idSite, principal, STOCK_CORRECT, ErrorCodes.STOCK_ACCESS_DENIED);
    return inventoryFacade.correct(idArticle, idSite, delta, reference, principal.getIdUtilisateur());
  }
}
