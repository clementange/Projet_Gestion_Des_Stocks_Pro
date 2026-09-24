package com.kfokam48.gestiondestock.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import com.kfokam48.gestiondestock.reporting.application.ReportingService;
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
 * Section 50 du prompt maitre : les agregations doivent etre coherentes avec les permissions de
 * l'utilisateur, et un responsable d'entrepot ne doit pas pouvoir voir les statistiques globales
 * s'il n'en a pas la permission.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ReportingServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ReportingService reportingService;

  @Autowired
  private InventoryFacade inventoryFacade;

  @Autowired
  private ArticleService articleService;

  @Autowired
  private CategoryService categoryService;

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private CityService cityService;

  @Autowired
  private SiteService siteService;

  @Autowired
  private PermissionService permissionService;

  @Autowired
  private RoleService roleService;

  @Autowired
  private UserRoleAssignmentService userRoleAssignmentService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private SiteDto createSite(CityDto city, String name) {
    return siteService.save(SiteDto.builder().code(uniqueCode("SITE")).name(name)
        .type(SiteType.ENTREPOT).active(true).city(city).build(), city.getOrganization().getId());
  }

  private ArticleDto createArticle() {
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    return articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());
  }

  /**
   * Le code "REPORTING_VIEW" est le code exact verifie par ReportingServiceImpl : il doit rester
   * un litteral fixe (pas de suffixe unique comme les autres codes de test), donc on le
   * recupere s'il existe deja plutot que de le recreer a chaque test/execution (la colonne code
   * est unique en base).
   */
  private PermissionDto getOrCreateReportingViewPermission() {
    try {
      return permissionService.findByCode("REPORTING_VIEW");
    } catch (EntityNotFoundException e) {
      return permissionService.save(PermissionDto.builder().code("REPORTING_VIEW").description("Consulter les rapports").build());
    }
  }

  @Test
  public void warehouseManagerCanSeeOwnSiteButNotGlobalOrOtherSites() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Report").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto mySite = createSite(city, "Entrepot A");
    SiteDto otherSite = createSite(city, "Entrepot B");
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), mySite.getId(), BigDecimal.valueOf(20), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);
    inventoryFacade.receive(article.getId(), otherSite.getId(), BigDecimal.valueOf(99), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    Long userId = 5001L;
    PermissionDto reportingView = getOrCreateReportingViewPermission();
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("WHM")).name("Warehouse manager").permissions(Set.of(reportingView)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(mySite.getId()).organizationId(organization.getId()).build());

    List<StockDto> myStock = reportingService.getStockForSite(userId, mySite.getId(), organization.getId());
    assertEquals(1, myStock.size());
    assertEquals(0, myStock.get(0).getQuantitePhysique().compareTo(BigDecimal.valueOf(20)));

    assertThrows(InvalidOperationException.class, () -> reportingService.getStockForSite(userId, otherSite.getId(), organization.getId()));
    assertThrows(InvalidOperationException.class, () -> reportingService.getGlobalStockSummary(userId, organization.getId()));
  }

  @Test
  public void directeurGeneralCanSeeGlobalSummaryAcrossSites() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe DG").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto siteA = createSite(city, "Entrepot A");
    SiteDto siteB = createSite(city, "Entrepot B");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), siteA.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);
    inventoryFacade.receive(article.getId(), siteB.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    Long dgUserId = 6001L;
    PermissionDto reportingView = getOrCreateReportingViewPermission();
    RoleDto dgRole = roleService.save(RoleDto.builder().code(uniqueCode("DG")).name("Directeur General").permissions(Set.of(reportingView)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(dgUserId).role(dgRole)
        .scopeType(ScopeType.GLOBAL).organizationId(organization.getId()).build());

    List<StockDto> global = reportingService.getGlobalStockSummary(dgUserId, organization.getId());
    long countForOurSites = global.stream()
        .filter(s -> s.getSite().getId().equals(siteA.getId()) || s.getSite().getId().equals(siteB.getId()))
        .count();
    assertEquals(2, countForOurSites);
  }

  @Test
  public void warehouseScopeConventionGrantsAccessToItsSite() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe WH").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto site = createSite(city, "Entrepot WH");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.valueOf(15), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    Long userId = 7001L;
    PermissionDto reportingView = getOrCreateReportingViewPermission();
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("WH")).name("Role warehouse-scope").permissions(Set.of(reportingView)).build());
    // Par convention documentee dans ReportingServiceImpl : scopeId WAREHOUSE = Site.id.
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.WAREHOUSE).scopeId(site.getId()).organizationId(organization.getId()).build());

    List<StockDto> stock = reportingService.getStockForSite(userId, site.getId(), organization.getId());
    assertEquals(1, stock.size());
  }

  @Test
  public void userWithoutAnyAssignmentIsDeniedEverything() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe None").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto site = createSite(city, "Entrepot Isole");

    Long userId = 8001L;

    assertThrows(InvalidOperationException.class, () -> reportingService.getStockForSite(userId, site.getId(), organization.getId()));
    assertThrows(InvalidOperationException.class, () -> reportingService.getGlobalStockSummary(userId, organization.getId()));
    assertTrue(reportingService.getLowStockReport(userId, organization.getId()).isEmpty());
  }
}
