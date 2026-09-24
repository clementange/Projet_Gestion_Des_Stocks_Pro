package com.kfokam48.gestiondestock.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Reproduit les tests 1 et 2 de la section 43 du prompt maitre (vente OK / vente refusee si
 * insuffisant), et verifie explicitement l'invariant de la section 25 : deux ventes concurrentes
 * ne doivent jamais faire passer le stock sous zero.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class InventoryFacadeIntegrationTest extends AbstractIntegrationTest {

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

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private SiteDto createSite(SiteType type) {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Inventory").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    return siteService.save(SiteDto.builder().code(uniqueCode("SITE")).name("Site test")
        .type(type).active(true).city(city).build(), organization.getId());
  }

  private ArticleDto createArticle() {
    SiteDto site = createSite(SiteType.BOUTIQUE);
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    ArticleDto article = articleService.save(
        ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
            .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
            .category(category).build());
    return article;
  }

  @Test
  public void saleWithinStockShouldReduceAvailableQuantity() {
    SiteDto site = createSite(SiteType.BOUTIQUE);
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    inventoryFacade.issue(article.getId(), site.getId(), BigDecimal.valueOf(3), StockMovementSource.VENTE, "VEN-001", null);

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.valueOf(7)));
  }

  @Test
  public void saleExceedingStockShouldBeRejected() {
    SiteDto site = createSite(SiteType.BOUTIQUE);
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    InvalidOperationException exception = org.junit.Assert.assertThrows(InvalidOperationException.class,
        () -> inventoryFacade.issue(article.getId(), site.getId(), BigDecimal.valueOf(15), StockMovementSource.VENTE, "VEN-002", null));

    assertEquals(com.kfokam48.gestiondestock.exception.ErrorCodes.STOCK_INSUFFICIENT, exception.getErrorCode());

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertEquals(0, stock.getQuantitePhysique().compareTo(BigDecimal.TEN));
  }

  @Test
  public void concurrentSalesShouldNeverOversell() throws Exception {
    SiteDto site = createSite(SiteType.BOUTIQUE);
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), site.getId(), BigDecimal.TEN, StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch goLatch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    List<Future<?>> futures = List.of(
        executor.submit(() -> attemptConcurrentIssue(article.getId(), site.getId(), BigDecimal.valueOf(8), readyLatch, goLatch, successCount, failureCount)),
        executor.submit(() -> attemptConcurrentIssue(article.getId(), site.getId(), BigDecimal.valueOf(7), readyLatch, goLatch, successCount, failureCount))
    );

    readyLatch.await(5, TimeUnit.SECONDS);
    goLatch.countDown();
    for (Future<?> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }
    executor.shutdown();

    // 8 + 7 = 15 > 10 : les deux ne peuvent pas reussir, sinon le stock deviendrait negatif.
    assertTrue(failureCount.get() >= 1, "Au moins une des deux ventes concurrentes doit echouer");
    assertFalse(successCount.get() == 2, "Les deux ventes concurrentes ne peuvent pas toutes les deux reussir");

    StockDto stock = inventoryFacade.getStock(article.getId(), site.getId());
    assertTrue(stock.getQuantitePhysique().compareTo(BigDecimal.ZERO) >= 0, "Le stock physique ne doit jamais devenir negatif");
  }

  private void attemptConcurrentIssue(Long articleId, Long siteId, BigDecimal quantity, CountDownLatch readyLatch,
      CountDownLatch goLatch, AtomicInteger successCount, AtomicInteger failureCount) {
    try {
      readyLatch.countDown();
      goLatch.await(5, TimeUnit.SECONDS);
      inventoryFacade.issue(articleId, siteId, quantity, StockMovementSource.VENTE, "VEN-CONCURRENT", null);
      successCount.incrementAndGet();
    } catch (InvalidOperationException | InterruptedException e) {
      failureCount.incrementAndGet();
    }
  }
}
