package com.kfokam48.gestiondestock.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Non-regression apres migration d'Article/Category vers le module catalog (Phase 5) : creation,
 * lecture, filtrage par categorie et rattachement a une Organization.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ArticleServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private ArticleService articleService;

  @Autowired
  private CategoryService categoryService;

  @Autowired
  private OrganizationService organizationService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  public void shouldSaveArticleAttachedToOrganizationAndCategory() {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Catalog").active(true).build());

    CategoryDto category = categoryService.save(
        CategoryDto.builder().code(uniqueCode("CAT")).designation("Informatique").organization(organization).build());

    ArticleDto article = articleService.save(
        ArticleDto.builder()
            .codeArticle(uniqueCode("ART"))
            .designation("Ordinateur portable")
            .prixUnitaireHt(BigDecimal.valueOf(500))
            .tauxTva(BigDecimal.valueOf(19.25))
            .prixUnitaireTtc(BigDecimal.valueOf(596.25))
            .category(category)
            .organization(organization)
            .build());

    assertNotNull(article.getId());
    assertEquals(category.getId(), article.getCategory().getId());
    assertEquals(organization.getId(), article.getOrganization().getId());

    ArticleDto found = articleService.findById(article.getId());
    assertEquals(article.getCodeArticle(), found.getCodeArticle());
    assertEquals(organization.getId(), found.getOrganization().getId());

    assertEquals(1, articleService.findAllArticleByIdCategory(category.getId()).size());
  }

  @Test
  public void shouldRejectArticleWithoutCategory() {
    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> articleService.save(
        ArticleDto.builder()
            .codeArticle(uniqueCode("ART"))
            .designation("Article sans categorie")
            .prixUnitaireHt(BigDecimal.TEN)
            .tauxTva(BigDecimal.ONE)
            .prixUnitaireTtc(BigDecimal.TEN)
            .build()));

    assertEquals(1, exception.getErrors().size());
  }

  @Test(expected = EntityNotFoundException.class)
  public void shouldThrowWhenArticleNotFound() {
    articleService.findById(0L);
  }
}
