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
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
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
 * Reproduit le cas metier central de la section 48 du prompt maitre : une vente affecte le
 * stock LOCAL du site vendeur, jamais un stock global, et est refusee si le stock local est
 * insuffisant meme si un autre site de la meme organisation est largement approvisionne
 * (decision actee du plan : pas d'auto-transfert en V1).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class SaleServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private SaleService saleService;

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
   * Phase 14 : SaleServiceImpl.create verifie desormais SALE_CREATE avant d'ecrire. Cree un
   * utilisateur de test avec cette permission accordee sur le site donne et retourne son ID.
   */
  private Long grantSaleCreate(SiteDto site) {
    PermissionDto permission;
    try {
      permission = permissionService.findByCode("SALE_CREATE");
    } catch (EntityNotFoundException e) {
      permission = permissionService.save(PermissionDto.builder().code("SALE_CREATE").description("Vente").build());
    }
    RoleDto role = roleService.save(RoleDto.builder().code(uniqueCode("ROLE")).name("Vendeur test").permissions(Set.of(permission)).build());
    Long userId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder().userId(userId).role(role)
        .scopeType(ScopeType.SITE).scopeId(site.getId()).build());
    return userId;
  }

  private SiteDto createBoutique(CityDto city, String name) {
    return siteService.save(SiteDto.builder().code(uniqueCode("BTQ")).name(name)
        .type(SiteType.BOUTIQUE).active(true).city(city).build());
  }

  private ArticleDto createArticle() {
    CategoryDto category = categoryService.save(CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat test").build());
    return articleService.save(ArticleDto.builder().codeArticle(uniqueCode("ART")).designation("Article test")
        .prixUnitaireHt(BigDecimal.TEN).tauxTva(BigDecimal.ONE).prixUnitaireTtc(BigDecimal.TEN)
        .category(category).build());
  }

  @Test
  public void saleShouldDecrementOnlyTheSellingSiteStock() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe X").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto akwa = createBoutique(douala, "Boutique Akwa");
    SiteDto bonamoussadi = createBoutique(douala, "Boutique Bonamoussadi");
    ArticleDto article = createArticle();

    inventoryFacade.receive(article.getId(), akwa.getId(), BigDecimal.valueOf(50), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-AKWA", null);
    inventoryFacade.receive(article.getId(), bonamoussadi.getId(), BigDecimal.valueOf(30), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-BONA", null);

    Long userId = grantSaleCreate(akwa);
    saleService.create(
        SaleDto.builder().code(uniqueCode("VEN")).site(akwa).build(),
        List.of(SaleLineDto.builder().article(article).quantite(BigDecimal.valueOf(10)).prixUnitaire(BigDecimal.TEN).build()),
        userId);

    StockDto stockAkwa = inventoryFacade.getStock(article.getId(), akwa.getId());
    StockDto stockBona = inventoryFacade.getStock(article.getId(), bonamoussadi.getId());
    assertEquals(0, stockAkwa.getQuantitePhysique().compareTo(BigDecimal.valueOf(40)));
    assertEquals(0, stockBona.getQuantitePhysique().compareTo(BigDecimal.valueOf(30)));
  }

  // Phase 5a : voir docs/phase-5a-report.md.
  @Test
  public void saleShouldRejectDuplicateCodeWithCleanErrorInsteadOfRaw500() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Dup").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto akwa = createBoutique(douala, "Boutique Akwa");
    ArticleDto article = createArticle();
    inventoryFacade.receive(article.getId(), akwa.getId(), BigDecimal.valueOf(50), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT", null);
    Long userId = grantSaleCreate(akwa);
    String code = uniqueCode("VEN");
    saleService.create(SaleDto.builder().code(code).site(akwa).build(),
        List.of(SaleLineDto.builder().article(article).quantite(BigDecimal.ONE).prixUnitaire(BigDecimal.TEN).build()), userId);

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> saleService.create(
        SaleDto.builder().code(code).site(akwa).build(),
        List.of(SaleLineDto.builder().article(article).quantite(BigDecimal.ONE).prixUnitaire(BigDecimal.TEN).build()), userId));

    assertEquals(ErrorCodes.SALE_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  public void saleShouldBeRejectedWhenLocalStockInsufficientEvenIfAnotherSiteIsWellStocked() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Y").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto akwa = createBoutique(douala, "Boutique Akwa");
    SiteDto entrepotCentral = createBoutique(douala, "Entrepot Central");
    ArticleDto article = createArticle();

    // Akwa n'a que 5 unites, l'entrepot central en a 500 : la vente a Akwa doit quand meme
    // etre refusee, sans bascule automatique sur le stock de l'entrepot (decision V1).
    inventoryFacade.receive(article.getId(), akwa.getId(), BigDecimal.valueOf(5), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-AKWA", null);
    inventoryFacade.receive(article.getId(), entrepotCentral.getId(), BigDecimal.valueOf(500), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-ENT", null);

    Long userId = grantSaleCreate(akwa);
    assertThrows(InvalidOperationException.class, () -> saleService.create(
        SaleDto.builder().code(uniqueCode("VEN")).site(akwa).build(),
        List.of(SaleLineDto.builder().article(article).quantite(BigDecimal.valueOf(10)).prixUnitaire(BigDecimal.TEN).build()),
        userId));

    StockDto stockAkwa = inventoryFacade.getStock(article.getId(), akwa.getId());
    StockDto stockEntrepot = inventoryFacade.getStock(article.getId(), entrepotCentral.getId());
    assertEquals(0, stockAkwa.getQuantitePhysique().compareTo(BigDecimal.valueOf(5)));
    assertEquals(0, stockEntrepot.getQuantitePhysique().compareTo(BigDecimal.valueOf(500)));
  }

  @Test
  public void multiLineSaleShouldRollBackEntirelyIfOneLineFails() {
    OrganizationDto organization = organizationService.save(OrganizationDto.builder().name("Societe Z").active(true).build());
    CityDto douala = cityService.save(CityDto.builder().name("Douala").organization(organization).build());
    SiteDto site = createBoutique(douala, "Boutique Test");
    ArticleDto articleOk = createArticle();
    ArticleDto articleInsuffisant = createArticle();

    inventoryFacade.receive(articleOk.getId(), site.getId(), BigDecimal.valueOf(20), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-1", null);
    inventoryFacade.receive(articleInsuffisant.getId(), site.getId(), BigDecimal.valueOf(2), StockMovementSource.COMMANDE_FOURNISSEUR, "INIT-2", null);

    Long userId = grantSaleCreate(site);
    assertThrows(InvalidOperationException.class, () -> saleService.create(
        SaleDto.builder().code(uniqueCode("VEN")).site(site).build(),
        List.of(
            SaleLineDto.builder().article(articleOk).quantite(BigDecimal.valueOf(5)).prixUnitaire(BigDecimal.TEN).build(),
            SaleLineDto.builder().article(articleInsuffisant).quantite(BigDecimal.valueOf(10)).prixUnitaire(BigDecimal.TEN).build()
        ),
        userId));

    // La premiere ligne (articleOk) doit avoir ete annulee (rollback) malgre son succes initial.
    StockDto stockOk = inventoryFacade.getStock(articleOk.getId(), site.getId());
    assertEquals(0, stockOk.getQuantitePhysique().compareTo(BigDecimal.valueOf(20)));
  }
}
