package com.kfokam48.gestiondestock.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CategoryServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private CategoryService service;

  @Autowired
  private OrganizationService organizationService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  public void shouldSaveCategoryWithSuccess() {
    CategoryDto expectedCategory = CategoryDto.builder()
        .code("Cat test")
        .designation("Designation test")
        .idEntreprise(1L)
        .build();

    CategoryDto savedCategory = service.save(expectedCategory);

    assertNotNull(savedCategory);
    assertNotNull(savedCategory.getId());
    assertEquals(expectedCategory.getCode(), savedCategory.getCode());
    assertEquals(expectedCategory.getDesignation(), savedCategory.getDesignation());
    assertEquals(expectedCategory.getIdEntreprise(), savedCategory.getIdEntreprise());
  }

  @Test
  public void shouldUpdateCategoryWithSuccess() {
    CategoryDto expectedCategory = CategoryDto.builder()
        .code("Cat test")
        .designation("Designation test")
        .idEntreprise(1L)
        .build();

    CategoryDto savedCategory = service.save(expectedCategory);

    CategoryDto categoryToUpdate = savedCategory;
    categoryToUpdate.setCode("Cat update");

    savedCategory = service.save(categoryToUpdate);

    assertNotNull(categoryToUpdate);
    assertNotNull(categoryToUpdate.getId());
    assertEquals(categoryToUpdate.getCode(), savedCategory.getCode());
    assertEquals(categoryToUpdate.getDesignation(), savedCategory.getDesignation());
    assertEquals(categoryToUpdate.getIdEntreprise(), savedCategory.getIdEntreprise());
  }

  @Test
  public void shouldThrowInvalidEntityException() {
    CategoryDto expectedCategory = CategoryDto.builder().build();

    InvalidEntityException expectedException = assertThrows(InvalidEntityException.class, () -> service.save(expectedCategory));

    assertEquals(ErrorCodes.CATEGORY_NOT_VALID, expectedException.getErrorCode());
    assertEquals(1, expectedException.getErrors().size());
    assertEquals("Veuillez renseigner le code de la categorie", expectedException.getErrors().get(0));
  }

  @Test
  public void shouldThrowEntityNotFoundException() {
    EntityNotFoundException expectedException = assertThrows(EntityNotFoundException.class, () -> service.findById(0L, null));

    assertEquals(ErrorCodes.CATEGORY_NOT_FOUND, expectedException.getErrorCode());
    assertEquals("Aucune category avec l'ID = 0 n' ete trouve dans la BDD", expectedException.getMessage());
  }

  @Test(expected = EntityNotFoundException.class)
  public void shouldThrowEntityNotFoundException2() {
    service.findById(0L, null);
  }

  // Phase 5b-2a : voir docs/phase-5b2a-report.md.
  @Test
  public void crossOrganizationReadsAreScoped() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe B").active(true).build());
    CategoryDto categoryA = service.save(
        CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat A").organization(orgA).build());

    assertEquals(categoryA.getId(), service.findById(categoryA.getId(), orgA.getId()).getId());
    assertEquals(categoryA.getCode(), service.findByCode(categoryA.getCode(), orgA.getId()).getCode());
    assertEquals(1, service.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> service.findById(categoryA.getId(), orgB.getId()));
    assertThrows(EntityNotFoundException.class, () -> service.findByCode(categoryA.getCode(), orgB.getId()));
    assertEquals(0, service.findAll(orgB.getId()).size());
  }

}
