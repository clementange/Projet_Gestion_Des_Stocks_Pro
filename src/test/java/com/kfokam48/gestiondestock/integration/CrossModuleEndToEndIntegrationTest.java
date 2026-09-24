package com.kfokam48.gestiondestock.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.PermissionService;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.WarehouseService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import com.kfokam48.gestiondestock.reporting.application.ReportingService;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import com.kfokam48.gestiondestock.transfers.application.StockTransferService;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 12 : rejoue le scenario complet de la section 48 du prompt maitre de bout en bout a
 * travers TOUS les modules (organization, catalog, purchasing, transfers, sales, inventory,
 * reporting) dans un seul flux coherent, plutot que module par module isolement comme le font
 * les tests des Phases 3-10.
 *
 * <p>Flux : reception fournisseur a l'entrepot central -> transfert vers une boutique -> vente
 * dans cette boutique -> vente refusee dans une autre boutique jamais approvisionnee -> lecture
 * reporting filtree par perimetre RBAC sur l'etat final.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CrossModuleEndToEndIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private OrganizationService organizationService;
  @Autowired
  private CityService cityService;
  @Autowired
  private SiteService siteService;
  @Autowired
  private WarehouseService warehouseService;
  @Autowired
  private CategoryService categoryService;
  @Autowired
  private ArticleService articleService;
  @Autowired
  private PurchaseOrderService purchaseOrderService;
  @Autowired
  private StockTransferService stockTransferService;
  @Autowired
  private SaleService saleService;
  @Autowired
  private ReportingService reportingService;
  @Autowired
  private PermissionService permissionService;
  @Autowired
  private RoleService roleService;
  @Autowired
  private UserRoleAssignmentService userRoleAssignmentService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private PermissionDto reportingViewPermission() {
    try {
      return permissionService.findByCode("REPORTING_VIEW");
    } catch (EntityNotFoundException e) {
      return permissionService.save(PermissionDto.builder().code("REPORTING_VIEW").description("Consulter les rapports").build());
    }
  }

  /**
   * Phase 14 : chaque action d'ecriture cross-module (reception, transfert, vente) verifie
   * desormais une permission SITE dediee. Accorde le code donne au userId sur ce site.
   */
  private void grantPermission(String code, SiteDto site, Long userId) {
    PermissionDto permission;
    try {
      permission = permissionService.findByCode(code);
    } catch (EntityNotFoundException e) {
      permission = permissionService.save(PermissionDto.builder().code(code).description(code).build());
    }
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Role e2e test").permissions(Set.of(permission)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).organizationId(organizationId).build());
  }

  private Long organizationId;

  @Test
  public void fullSupplyChainFlowAcrossAllModules() {
    // 1. Organisation : Societe X a Douala, avec un entrepot central et deux boutiques.
    OrganizationDto societeX = organizationService.save(OrganizationDto.builder().name("Societe X").active(true).build());
    organizationId = societeX.getId();
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(societeX).build());

    SiteDto entrepotCentral = siteService.save(SiteDto.builder().code(uniqueCode("ENT-DLA")).name("Entrepot Central Douala")
        .type(SiteType.ENTREPOT).active(true).city(douala).build());
    WarehouseDto warehouse = warehouseService.save(WarehouseDto.builder().code(uniqueCode("WH-DLA")).name("Entrepot Central Douala")
        .site(entrepotCentral).build());
    assertEquals(entrepotCentral.getId(), warehouse.getSite().getId());

    SiteDto boutiqueAkwa = siteService.save(SiteDto.builder().code(uniqueCode("BTQ-AKW")).name("Boutique Akwa")
        .type(SiteType.BOUTIQUE).active(true).city(douala).build());
    SiteDto boutiqueBonamoussadi = siteService.save(SiteDto.builder().code(uniqueCode("BTQ-BON")).name("Boutique Bonamoussadi")
        .type(SiteType.BOUTIQUE).active(true).city(douala).build());

    // 2. Catalogue : article ORD-001.
    CategoryDto categorie = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Informatique").organization(societeX).build());
    ArticleDto ord001 = articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ORD-001")).designation("Ordinateur HP")
        .prixUnitaireHt(BigDecimal.valueOf(400)).tauxTva(BigDecimal.valueOf(19.25)).prixUnitaireTtc(BigDecimal.valueOf(477))
        .category(categorie).organization(societeX).build());

    // 3. Achat : commande fournisseur de 500 unites, receptionnee integralement a l'entrepot.
    PurchaseOrderDto commandeFournisseur = purchaseOrderService.create(
        PurchaseOrderDto.builder().code(uniqueCode("PO")).supplierId(1L).site(entrepotCentral).build(),
        List.of(PurchaseOrderLineDto.builder().article(ord001).quantiteCommandee(BigDecimal.valueOf(500)).prixUnitaire(BigDecimal.valueOf(400)).build()),
        organizationId);
    purchaseOrderService.validate(commandeFournisseur.getId());
    Long ligneCommande = purchaseOrderService.findLines(commandeFournisseur.getId()).get(0).getId();
    grantPermission("PURCHASE_ORDER_RECEIVE", entrepotCentral, 10L);
    purchaseOrderService.receiveLine(commandeFournisseur.getId(), ligneCommande, BigDecimal.valueOf(500), 10L, organizationId);

    assertEquals(0, stockOf(ord001.getId(), entrepotCentral.getId()).getQuantitePhysique().compareTo(BigDecimal.valueOf(500)));

    // 4. Transfert : 50 unites de l'entrepot central vers la boutique Akwa.
    StockTransferDto transfert = stockTransferService.create(
        StockTransferDto.builder().code(uniqueCode("TR")).originSite(entrepotCentral).destinationSite(boutiqueAkwa).requestedByUserId(10L).build(),
        List.of(StockTransferLineDto.builder().article(ord001).quantite(BigDecimal.valueOf(50)).build()),
        organizationId);
    stockTransferService.submit(transfert.getId());
    stockTransferService.approve(transfert.getId(), 11L);
    stockTransferService.startPreparation(transfert.getId());
    grantPermission("STOCK_TRANSFER_SHIP", entrepotCentral, 12L);
    stockTransferService.ship(transfert.getId(), 12L, organizationId);
    grantPermission("STOCK_TRANSFER_RECEIVE", boutiqueAkwa, 13L);
    stockTransferService.receive(transfert.getId(), 13L, organizationId);

    assertEquals(0, stockOf(ord001.getId(), entrepotCentral.getId()).getQuantitePhysique().compareTo(BigDecimal.valueOf(450)));
    assertEquals(0, stockOf(ord001.getId(), boutiqueAkwa.getId()).getQuantitePhysique().compareTo(BigDecimal.valueOf(50)));

    // 5. Vente de 10 unites a la boutique Akwa, qui a du stock local.
    grantPermission("SALE_CREATE", boutiqueAkwa, 14L);
    saleService.create(
        SaleDto.builder().code(uniqueCode("VEN")).site(boutiqueAkwa).build(),
        List.of(SaleLineDto.builder().article(ord001).quantite(BigDecimal.valueOf(10)).prixUnitaire(BigDecimal.valueOf(477)).build()),
        14L, organizationId);
    assertEquals(0, stockOf(ord001.getId(), boutiqueAkwa.getId()).getQuantitePhysique().compareTo(BigDecimal.valueOf(40)));

    // 6. La boutique Bonamoussadi n'a jamais ete approvisionnee : toute vente y est refusee,
    // sans bascule automatique sur le stock de l'entrepot ou d'une autre boutique (section 48).
    // (Permission accordee ici aussi : on verifie le refus pour stock insuffisant, pas un refus
    // d'acces qui masquerait la vraie garantie testee.)
    grantPermission("SALE_CREATE", boutiqueBonamoussadi, 15L);
    assertThrows(InvalidOperationException.class, () -> saleService.create(
        SaleDto.builder().code(uniqueCode("VEN")).site(boutiqueBonamoussadi).build(),
        List.of(SaleLineDto.builder().article(ord001).quantite(BigDecimal.ONE).prixUnitaire(BigDecimal.valueOf(477)).build()),
        15L, organizationId));

    // 7. Reporting : un Directeur General (scope GLOBAL) voit l'etat final complet.
    List<StockDto> global = reportingService.getGlobalStockSummary(globalUserId(), organizationId);
    BigDecimal totalToutesSites = global.stream()
        .filter(s -> ord001.getId().equals(s.getArticle().getId()))
        .map(StockDto::getQuantitePhysique)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    // 450 (entrepot) + 40 (Akwa) + 0 (Bonamoussadi, jamais approvisionnee -> aucune ligne Stock)
    assertEquals(0, totalToutesSites.compareTo(BigDecimal.valueOf(490)));

    // 8. Un responsable limite a la boutique Akwa voit son site mais pas l'entrepot central.
    Long akwaManagerId = 9001L;
    PermissionDto reportingView = reportingViewPermission();
    RoleDto akwaRole = roleService.save(RoleDto.builder().code(uniqueCode("AKWA_MGR")).name("Responsable Akwa").permissions(Set.of(reportingView)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(akwaManagerId).role(akwaRole)
        .scopeType(ScopeType.SITE).scopeId(boutiqueAkwa.getId()).organizationId(organizationId).build());

    List<StockDto> vueAkwa = reportingService.getStockForSite(akwaManagerId, boutiqueAkwa.getId(), organizationId);
    assertEquals(1, vueAkwa.size());
    assertEquals(0, vueAkwa.get(0).getQuantitePhysique().compareTo(BigDecimal.valueOf(40)));
    assertThrows(InvalidOperationException.class, () -> reportingService.getStockForSite(akwaManagerId, entrepotCentral.getId(), organizationId));
  }

  private StockDto stockOf(Long articleId, Long siteId) {
    List<StockDto> stock = reportingService.getStockForSite(globalUserId(), siteId, organizationId).stream()
        .filter(s -> articleId.equals(s.getArticle().getId()))
        .toList();
    return stock.isEmpty() ? StockDto.builder().quantitePhysique(BigDecimal.ZERO).build() : stock.get(0);
  }

  private Long globalUserIdCache;

  private Long globalUserId() {
    if (globalUserIdCache != null) {
      return globalUserIdCache;
    }
    Long userId = 9999L;
    PermissionDto reportingView = reportingViewPermission();
    RoleDto dgRole = roleService.save(RoleDto.builder().code(uniqueCode("DG")).name("Directeur General").permissions(Set.of(reportingView)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(dgRole).scopeType(ScopeType.GLOBAL).organizationId(organizationId).build());
    globalUserIdCache = userId;
    return userId;
  }
}
