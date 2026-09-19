package com.kfokam48.gestiondestock.integration;

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
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 12 : la Phase 6 a deja prouve que deux ventes concurrentes sur le meme Stock ne peuvent
 * jamais le faire passer sous zero. Ce test verifie que la meme garantie tient quand les deux
 * operations concurrentes viennent de DEUX modules differents (sales.Sale et
 * sales.CustomerOrder), qui passent tous deux par InventoryFacade mais avec des chemins de code
 * distincts (issue() direct vs reserve()) : la protection est bien portee par l'agregat Stock
 * lui-meme (verrouillage optimiste), pas par une coincidence d'implementation d'un seul module.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CrossModuleConcurrencyIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private InventoryFacade inventoryFacade;
  @Autowired
  private SaleService saleService;
  @Autowired
  private CustomerOrderService customerOrderService;
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
   * Phase 14 : SaleServiceImpl.create et CustomerOrderServiceImpl.reserve verifient desormais
   * une permission SITE dediee. Accorde le code donne a un nouvel utilisateur de test sur ce
   * site et retourne son ID, pour que la course mesure bien un conflit de verrouillage optimiste
   * sur Stock, pas un refus d'acces.
   */
  private Long grantPermission(String code, SiteDto site, Long organizationId) {
    PermissionDto permission;
    try {
      permission = permissionService.findByCode(code);
    } catch (EntityNotFoundException e) {
      permission = permissionService.save(PermissionDto.builder().code(code).description(code).build());
    }
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Role concurrence test").permissions(Set.of(permission)).build());
    Long userId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).organizationId(organizationId).build());
    return userId;
  }

  @Test
  public void saleAndCustomerOrderReservationRacingOnSameStockNeverOversell() throws Exception {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Concurrence").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto site = siteService.save(SiteDto.builder().code(uniqueCode("SITE")).name("Boutique concurrence")
        .type(SiteType.BOUTIQUE).active(true).city(city).build());
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    ArticleDto article = articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());

    // Stock = 10. Une vente directe de 8 (module sales.Sale, InventoryFacade.issue) et une
    // reservation de commande client de 7 (module sales.CustomerOrder, InventoryFacade.reserve)
    // partent en meme temps : 8 + 7 = 15 > 10, les deux ne peuvent pas toutes les deux reussir.
    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    CustomerOrderDto order = customerOrderService.create(
        CustomerOrderDto.builder().code(uniqueCode("CMD")).customerId(1L).site(site).build(),
        List.of(CustomerOrderLineDto.builder().article(article).quantite(BigDecimal.valueOf(7)).prixUnitaire(BigDecimal.TEN).build()));
    customerOrderService.validate(order.getId());

    Long saleUserId = grantPermission("SALE_CREATE", site, organization.getId());
    Long reserveUserId = grantPermission("CUSTOMER_ORDER_RESERVE", site, organization.getId());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch goLatch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    List<Future<?>> futures = List.of(
        executor.submit(() -> race(() -> saleService.create(
                SaleDto.builder().code(uniqueCode("VEN")).site(site).build(),
                List.of(SaleLineDto.builder().article(article).quantite(BigDecimal.valueOf(8)).prixUnitaire(BigDecimal.TEN).build()),
                saleUserId, organization.getId()),
            readyLatch, goLatch, successCount, failureCount)),
        executor.submit(() -> race(() -> customerOrderService.reserve(order.getId(), reserveUserId, organization.getId()),
            readyLatch, goLatch, successCount, failureCount))
    );

    readyLatch.await(5, TimeUnit.SECONDS);
    goLatch.countDown();
    for (Future<?> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertTrue(failureCount.get() >= 1, "Au moins une des deux operations concurrentes doit echouer");

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertTrue(stock.getQuantitePhysique().compareTo(BigDecimal.ZERO) >= 0, "Le stock physique ne doit jamais devenir negatif");
    assertTrue(stock.getQuantiteReservee().compareTo(stock.getQuantitePhysique()) <= 0,
        "La quantite reservee ne doit jamais depasser la quantite physique");
  }

  private void race(Runnable operation, CountDownLatch readyLatch, CountDownLatch goLatch,
      AtomicInteger successCount, AtomicInteger failureCount) {
    try {
      readyLatch.countDown();
      goLatch.await(5, TimeUnit.SECONDS);
      operation.run();
      successCount.incrementAndGet();
    } catch (InvalidOperationException | InterruptedException e) {
      failureCount.incrementAndGet();
    }
  }
}
