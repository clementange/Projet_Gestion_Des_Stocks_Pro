package com.kfokam48.gestiondestock.sales;

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
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Verifie le cycle de vie complet de la commande client (Livrable 7) : la reservation bloque le
 * stock disponible sans le sortir physiquement, et seule la livraison sort reellement le stock
 * et libere la reservation.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CustomerOrderServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private CustomerOrderService customerOrderService;

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
   * Phase 14 : reserve/deliver/cancel verifient desormais une permission dediee. Cree un
   * utilisateur de test avec exactement les permissions demandees, accordees sur ce site.
   */
  private Long grantOrderPermissions(SiteDto site, Long organizationId, String... codes) {
    Set<PermissionDto> permissions = Arrays.stream(codes).map(code -> {
      try {
        return permissionService.findByCode(code);
      } catch (EntityNotFoundException e) {
        return permissionService.save(PermissionDto.builder().code(code).description(code).build());
      }
    }).collect(Collectors.toSet());
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Role commande client test").permissions(permissions).build());
    Long userId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).organizationId(organizationId).build());
    return userId;
  }

  private OrganizationDto organization;

  private SiteDto createSite() {
    organization = organizationService.save(OrganizationDto.builder().name("Societe Sales").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    return siteService.save(SiteDto.builder().code(uniqueCode("BTQ")).name("Boutique test")
        .type(SiteType.BOUTIQUE).active(true).city(city).build());
  }

  private ArticleDto createArticle() {
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    return articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());
  }

  private CustomerOrderDto createOrder(SiteDto site, ArticleDto article, BigDecimal quantity, Long[] lineIdOut) {
    CustomerOrderDto order = customerOrderService.create(
        CustomerOrderDto.builder().code(uniqueCode("CMD")).customerId(1L).site(site).build(),
        List.of(CustomerOrderLineDto.builder().article(article).quantite(quantity).prixUnitaire(BigDecimal.TEN).build()));
    lineIdOut[0] = customerOrderService.findLines(order.getId()).get(0).getId();
    return order;
  }

  // Phase 5a : voir docs/phase-5a-report.md.
  @Test
  public void createShouldRejectDuplicateCodeWithCleanErrorInsteadOfRaw500() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    String code = uniqueCode("CMD");
    customerOrderService.create(CustomerOrderDto.builder().code(code).customerId(1L).site(site).build(),
        List.of(CustomerOrderLineDto.builder().article(article).quantite(BigDecimal.ONE).prixUnitaire(BigDecimal.TEN).build()));

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> customerOrderService.create(
        CustomerOrderDto.builder().code(code).customerId(1L).site(site).build(),
        List.of(CustomerOrderLineDto.builder().article(article).quantite(BigDecimal.ONE).prixUnitaire(BigDecimal.TEN).build())));

    assertEquals(ErrorCodes.CUSTOMER_ORDER_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  public void reservationShouldReduceAvailableButNotPhysicalStock() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = createOrder(site, article, BigDecimal.valueOf(6), new Long[1]);
    customerOrderService.validate(order.getId());
    Long userId = grantOrderPermissions(site, organization.getId(), "CUSTOMER_ORDER_RESERVE");
    customerOrderService.reserve(order.getId(), userId, organization.getId());

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.TEN));
    assertEquals(0, stock.getQuantiteReservee().compareTo(BigDecimal.valueOf(6)));
    assertEquals(0, stock.getQuantiteDisponible().compareTo(BigDecimal.valueOf(4)));
  }

  @Test
  public void reservationExceedingAvailableStockShouldBeRejected() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.valueOf(5), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = createOrder(site, article, BigDecimal.TEN, new Long[1]);
    customerOrderService.validate(order.getId());
    Long userId = grantOrderPermissions(site, organization.getId(), "CUSTOMER_ORDER_RESERVE");

    assertThrows(InvalidOperationException.class, () -> customerOrderService.reserve(order.getId(), userId, organization.getId()));
  }

  @Test
  public void fullLifecycleShouldIssueStockAndReleaseReservationOnlyOnDelivery() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = createOrder(site, article, BigDecimal.valueOf(6), new Long[1]);
    customerOrderService.validate(order.getId());
    Long userId = grantOrderPermissions(site, organization.getId(), "CUSTOMER_ORDER_RESERVE", "CUSTOMER_ORDER_DELIVER");
    customerOrderService.reserve(order.getId(), userId, organization.getId());
    customerOrderService.prepare(order.getId());
    customerOrderService.ship(order.getId());

    StockDto beforeDelivery = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, beforeDelivery.getQuantitePhysique().compareTo(BigDecimal.TEN));

    customerOrderService.deliver(order.getId(), userId, organization.getId());

    StockDto afterDelivery = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, afterDelivery.getQuantitePhysique().compareTo(BigDecimal.valueOf(4)));
    assertEquals(0, afterDelivery.getQuantiteReservee().compareTo(BigDecimal.ZERO));
    assertEquals(CustomerOrderStatus.LIVREE, customerOrderService.findById(order.getId()).getStatus());
  }

  @Test
  public void cancelAfterReservationShouldReleaseReservation() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = createOrder(site, article, BigDecimal.valueOf(6), new Long[1]);
    customerOrderService.validate(order.getId());
    Long userId = grantOrderPermissions(site, organization.getId(), "CUSTOMER_ORDER_RESERVE", "CUSTOMER_ORDER_CANCEL");
    customerOrderService.reserve(order.getId(), userId, organization.getId());

    customerOrderService.cancel(order.getId(), userId, organization.getId());

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantiteReservee().compareTo(BigDecimal.ZERO));
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.TEN));
    assertEquals(CustomerOrderStatus.ANNULEE, customerOrderService.findById(order.getId()).getStatus());
  }

  @Test
  public void deliveryBeforeShippingShouldBeRejected() {
    SiteDto site = createSite();
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = createOrder(site, article, BigDecimal.valueOf(6), new Long[1]);
    customerOrderService.validate(order.getId());
    Long userId = grantOrderPermissions(site, organization.getId(), "CUSTOMER_ORDER_RESERVE", "CUSTOMER_ORDER_DELIVER");
    customerOrderService.reserve(order.getId(), userId, organization.getId());

    assertThrows(InvalidOperationException.class, () -> customerOrderService.deliver(order.getId(), userId, organization.getId()));
  }
}
