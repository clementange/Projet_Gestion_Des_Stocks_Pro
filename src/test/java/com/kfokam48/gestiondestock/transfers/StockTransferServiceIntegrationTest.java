package com.kfokam48.gestiondestock.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
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
import com.kfokam48.gestiondestock.transfers.application.StockTransferService;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import com.kfokam48.gestiondestock.transfers.domain.model.TransferStatus;
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
 * Reproduit le Test 3 de la section 43 du prompt maitre : Entrepot A = 100, transfert A -> B =
 * 30, resultat A = 70 / B = 30 ; et verifie le cycle de vie complet de la section 15.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class StockTransferServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private StockTransferService stockTransferService;

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

  /**
   * Phase 14 : ship/receive verifient desormais une permission dediee sur le site
   * origine/destination. Accorde ce code au userId donne sur ce site.
   */
  private void grantTransferPermission(String code, SiteDto site, Long userId, Long organizationId) {
    PermissionDto permission;
    try {
      permission = permissionService.findByCode(code);
    } catch (EntityNotFoundException e) {
      permission = permissionService.save(PermissionDto.builder().code(code).description(code).build());
    }
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Role transfert test").permissions(Set.of(permission)).build());
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).organizationId(organizationId).build());
  }

  private SiteDto createSite(CityDto city, String name) {
    return siteService.save(SiteDto.builder().code(uniqueCode("ENT")).name(name)
        .type(SiteType.ENTREPOT).active(true).city(city).build(), city.getOrganization().getId());
  }

  private ArticleDto createArticle() {
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    return articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());
  }

  private StockTransferDto createTransfer(SiteDto origin, SiteDto destination, ArticleDto article, BigDecimal quantity, Long organizationId) {
    return stockTransferService.create(
        StockTransferDto.builder().code(uniqueCode("TR")).originSite(origin).destinationSite(destination).requestedByUserId(1L).build(),
        List.of(StockTransferLineDto.builder().article(article).quantite(quantity).build()),
        organizationId);
  }

  private StockTransferDto runToShippable(StockTransferDto transfer) {
    stockTransferService.submit(transfer.getId());
    stockTransferService.approve(transfer.getId(), 2L);
    return stockTransferService.startPreparation(transfer.getId());
  }

  @Test
  public void fullTransferShouldMoveStockFromOriginToDestination() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Transfer").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto entrepotA = createSite(douala, "Entrepot A");
    SiteDto entrepotB = createSite(douala, "Entrepot B");
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), entrepotA.getId(), BigDecimal.valueOf(100),
        StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-A", null);

    StockTransferDto transfer = createTransfer(entrepotA, entrepotB, article, BigDecimal.valueOf(30), organization.getId());
    runToShippable(transfer);
    grantTransferPermission("STOCK_TRANSFER_SHIP", entrepotA, 3L, organization.getId());
    grantTransferPermission("STOCK_TRANSFER_RECEIVE", entrepotB, 4L, organization.getId());
    stockTransferService.ship(transfer.getId(), 3L, organization.getId());
    stockTransferService.receive(transfer.getId(), 4L, organization.getId());

    StockDto stockA = inventoryFacade.getStock(article.getId(), entrepotA.getId());
    StockDto stockB = inventoryFacade.getStock(article.getId(), entrepotB.getId());
    assertEquals(0, stockA.getQuantitePhysique().compareTo(BigDecimal.valueOf(70)));
    assertEquals(0, stockB.getQuantitePhysique().compareTo(BigDecimal.valueOf(30)));
    assertEquals(TransferStatus.RECU, stockTransferService.findById(transfer.getId(), organization.getId()).getStatus());
  }

  @Test
  public void originAndDestinationMustDiffer() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Bad").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto site = createSite(douala, "Entrepot Unique");
    ArticleDto article = createArticle();

    assertThrows(InvalidEntityException.class, () -> createTransfer(site, site, article, BigDecimal.TEN, organization.getId()));
  }

  @Test
  public void shippingBeforePreparationShouldBeRejected() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Seq").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto entrepotA = createSite(douala, "Entrepot A");
    SiteDto entrepotB = createSite(douala, "Entrepot B");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), entrepotA.getId(), BigDecimal.valueOf(50),
        StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    StockTransferDto transfer = createTransfer(entrepotA, entrepotB, article, BigDecimal.TEN, organization.getId());
    Long userId = 30L;
    grantTransferPermission("STOCK_TRANSFER_SHIP", entrepotA, userId, organization.getId());

    assertThrows(InvalidOperationException.class, () -> stockTransferService.ship(transfer.getId(), userId, organization.getId()));
  }

  @Test
  public void shippingMoreThanAvailableAtOriginShouldBeRejected() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Insuff").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto entrepotA = createSite(douala, "Entrepot A");
    SiteDto entrepotB = createSite(douala, "Entrepot B");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), entrepotA.getId(), BigDecimal.valueOf(5),
        StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    StockTransferDto transfer = createTransfer(entrepotA, entrepotB, article, BigDecimal.TEN, organization.getId());
    runToShippable(transfer);
    Long userId = 31L;
    grantTransferPermission("STOCK_TRANSFER_SHIP", entrepotA, userId, organization.getId());

    assertThrows(InvalidOperationException.class, () -> stockTransferService.ship(transfer.getId(), userId, organization.getId()));

    StockDto stockA = inventoryFacade.getStock(article.getId(), entrepotA.getId());
    assertEquals(0, stockA.getQuantitePhysique().compareTo(BigDecimal.valueOf(5)));
  }

  @Test
  public void cancelAfterShippingShouldBeRejected() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Cancel").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto entrepotA = createSite(douala, "Entrepot A");
    SiteDto entrepotB = createSite(douala, "Entrepot B");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), entrepotA.getId(), BigDecimal.valueOf(50),
        StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    StockTransferDto transfer = createTransfer(entrepotA, entrepotB, article, BigDecimal.TEN, organization.getId());
    runToShippable(transfer);
    Long userId = 32L;
    grantTransferPermission("STOCK_TRANSFER_SHIP", entrepotA, userId, organization.getId());
    stockTransferService.ship(transfer.getId(), userId, organization.getId());

    assertThrows(InvalidOperationException.class, () -> stockTransferService.cancel(transfer.getId()));
  }

  // Phase 5b-2b : voir docs/phase-5b2b-report.md.
  @Test
  public void crossOrganizationReadsAreScoped() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe Transfer Read A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe Transfer Read B").active(true).build());
    CityDto doualaA = cityService.save(CityDto.builder().name("Douala").organization(orgA).build());
    SiteDto entrepotA = createSite(doualaA, "Entrepot A");
    SiteDto entrepotB = createSite(doualaA, "Entrepot B");
    ArticleDto article = createArticle();
    StockTransferDto transfer = createTransfer(entrepotA, entrepotB, article, BigDecimal.TEN, orgA.getId());

    assertEquals(transfer.getId(), stockTransferService.findById(transfer.getId(), orgA.getId()).getId());
    assertEquals(transfer.getId(), stockTransferService.findByCode(transfer.getCode(), orgA.getId()).getId());
    assertEquals(1, stockTransferService.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> stockTransferService.findById(transfer.getId(), orgB.getId()));
    assertThrows(EntityNotFoundException.class, () -> stockTransferService.findByCode(transfer.getCode(), orgB.getId()));
    assertEquals(0, stockTransferService.findAll(orgB.getId()).size());
  }

  /**
   * Phase 5b-2b : StockTransfer.create n'avait avant ce correctif aucune verification de
   * permission ni d'appartenance de site (ni origine ni destination) - voir
   * docs/phase-5b2b-report.md.
   */
  @Test
  public void createShouldRejectOriginSiteFromAnotherOrganization() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe Transfer Origin Caller").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe Transfer Origin Foreign").active(true).build());
    CityDto doualaA = cityService.save(CityDto.builder().name("Douala").organization(orgA).build());
    CityDto doualaB = cityService.save(CityDto.builder().name("Douala").organization(orgB).build());
    SiteDto foreignOrigin = createSite(doualaB, "Entrepot etranger origine");
    SiteDto destination = createSite(doualaA, "Entrepot destination");
    ArticleDto article = createArticle();

    InvalidOperationException exception = assertThrows(InvalidOperationException.class,
        () -> createTransfer(foreignOrigin, destination, article, BigDecimal.TEN, orgA.getId()));

    assertEquals(ErrorCodes.STOCK_TRANSFER_ACCESS_DENIED, exception.getErrorCode());
  }

  /**
   * Phase 5b-2b : le site d'origine peut appartenir a l'organisation de l'appelant tout en ayant
   * une destination etrangere - les DEUX sites doivent etre verifies independamment - voir
   * docs/phase-5b2b-report.md.
   */
  @Test
  public void createShouldRejectDestinationSiteFromAnotherOrganization() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe Transfer Dest Caller").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe Transfer Dest Foreign").active(true).build());
    CityDto doualaA = cityService.save(CityDto.builder().name("Douala").organization(orgA).build());
    CityDto doualaB = cityService.save(CityDto.builder().name("Douala").organization(orgB).build());
    SiteDto origin = createSite(doualaA, "Entrepot origine");
    SiteDto foreignDestination = createSite(doualaB, "Entrepot etranger destination");
    ArticleDto article = createArticle();

    InvalidOperationException exception = assertThrows(InvalidOperationException.class,
        () -> createTransfer(origin, foreignDestination, article, BigDecimal.TEN, orgA.getId()));

    assertEquals(ErrorCodes.STOCK_TRANSFER_ACCESS_DENIED, exception.getErrorCode());
  }
}
