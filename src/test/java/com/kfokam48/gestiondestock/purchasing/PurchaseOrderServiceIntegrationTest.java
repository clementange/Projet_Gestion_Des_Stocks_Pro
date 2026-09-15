package com.kfokam48.gestiondestock.purchasing;

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
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Verifie le workflow cible de la section 14 : la creation et la validation d'une commande
 * fournisseur ne touchent jamais au stock ; seule une reception explicite le fait, et
 * correctement (Phase 7, "Test reception -> entree stock correcte").
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class PurchaseOrderServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PurchaseOrderService purchaseOrderService;

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
   * Phase 14 : receiveLine verifie desormais PURCHASE_ORDER_RECEIVE. Cree un utilisateur de test
   * avec cette permission accordee sur le site receveur et retourne son ID.
   */
  private Long grantPurchaseOrderReceive(SiteDto site) {
    PermissionDto permission;
    try {
      permission = permissionService.findByCode("PURCHASE_ORDER_RECEIVE");
    } catch (EntityNotFoundException e) {
      permission = permissionService.save(PermissionDto.builder().code("PURCHASE_ORDER_RECEIVE").description("Reception fournisseur").build());
    }
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Magasinier test").permissions(Set.of(permission)).build());
    Long userId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).build());
    return userId;
  }

  private SiteDto createReceivingSite() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Purchasing").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    return siteService.save(SiteDto.builder().code(uniqueCode("SITE")).name("Entrepot reception")
        .type(SiteType.ENTREPOT).active(true).city(city).build());
  }

  private ArticleDto createArticle() {
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    return articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());
  }

  private PurchaseOrderDto createDraftOrder(SiteDto site, ArticleDto article, BigDecimal quantity, Long[] lineIdOut) {
    PurchaseOrderDto order = purchaseOrderService.create(
        PurchaseOrderDto.builder().code(uniqueCode("PO")).supplierId(1L).site(site).build(),
        List.of(PurchaseOrderLineDto.builder().article(article).quantiteCommandee(quantity).prixUnitaire(BigDecimal.TEN).build()));
    lineIdOut[0] = purchaseOrderService.findLines(order.getId()).get(0).getId();
    return order;
  }

  @Test
  public void creatingOrderShouldNotAffectStock() {
    SiteDto site = createReceivingSite();
    ArticleDto article = createArticle();

    createDraftOrder(site, article, BigDecimal.TEN, new Long[1]);

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.ZERO));
  }

  @Test
  public void receptionBeforeValidationShouldBeRejected() {
    SiteDto site = createReceivingSite();
    ArticleDto article = createArticle();
    Long[] lineId = new Long[1];
    PurchaseOrderDto order = createDraftOrder(site, article, BigDecimal.TEN, lineId);
    Long userId = grantPurchaseOrderReceive(site);

    assertThrows(InvalidOperationException.class,
        () -> purchaseOrderService.receiveLine(order.getId(), lineId[0], BigDecimal.TEN, userId));
  }

  @Test
  public void fullReceptionShouldIncreaseStockAndCloseOrder() {
    SiteDto site = createReceivingSite();
    ArticleDto article = createArticle();
    Long[] lineId = new Long[1];
    PurchaseOrderDto order = createDraftOrder(site, article, BigDecimal.TEN, lineId);

    purchaseOrderService.validate(order.getId());
    Long userId = grantPurchaseOrderReceive(site);
    purchaseOrderService.receiveLine(order.getId(), lineId[0], BigDecimal.TEN, userId);

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.TEN));
    assertEquals(PurchaseOrderStatus.RECUE, purchaseOrderService.findById(order.getId()).getStatus());
  }

  @Test
  public void partialReceptionsShouldAccumulateAndCloseOrderOnlyWhenComplete() {
    SiteDto site = createReceivingSite();
    ArticleDto article = createArticle();
    Long[] lineId = new Long[1];
    PurchaseOrderDto order = createDraftOrder(site, article, BigDecimal.TEN, lineId);
    purchaseOrderService.validate(order.getId());
    Long userId = grantPurchaseOrderReceive(site);

    purchaseOrderService.receiveLine(order.getId(), lineId[0], BigDecimal.valueOf(4), userId);
    assertEquals(PurchaseOrderStatus.VALIDEE, purchaseOrderService.findById(order.getId()).getStatus());

    purchaseOrderService.receiveLine(order.getId(), lineId[0], BigDecimal.valueOf(6), userId);
    assertEquals(PurchaseOrderStatus.RECUE, purchaseOrderService.findById(order.getId()).getStatus());

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.TEN));
  }

  @Test
  public void overReceptionShouldBeRejected() {
    SiteDto site = createReceivingSite();
    ArticleDto article = createArticle();
    Long[] lineId = new Long[1];
    PurchaseOrderDto order = createDraftOrder(site, article, BigDecimal.TEN, lineId);
    purchaseOrderService.validate(order.getId());
    Long userId = grantPurchaseOrderReceive(site);

    assertThrows(InvalidOperationException.class,
        () -> purchaseOrderService.receiveLine(order.getId(), lineId[0], BigDecimal.valueOf(15), userId));
  }
}
