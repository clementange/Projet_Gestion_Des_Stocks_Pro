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
    // Phase 5c : la creation exige CATEGORY_CREATE dans l'organisation ciblee. Le decor fournit
    // donc une organisation reelle et un utilisateur autorise - voir docs/phase-5c-report.md.
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Cat Save").active(true).build());
    CategoryDto expectedCategory = CategoryDto.builder()
        .code("Cat test")
        .designation("Designation test")
        .idEntreprise(1L)
        .organization(organization)
        .build();

    CategoryDto savedCategory = service.save(expectedCategory,
        userWithAllWritePermissions(organization.getId()), organization.getId());

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

    // Phase 5b-2g : save() n'est plus un upsert. La mise a jour passe par
    // update(id, dto, organizationId), qui exige l'appartenance : la categorie est donc creee
    // avec une organisation reelle - voir docs/phase-5b2g-report.md.
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Cat Update").active(true).build());
    expectedCategory.setOrganization(organization);
    Long userId = userWithAllWritePermissions(organization.getId());
    CategoryDto savedCategory = service.save(expectedCategory, userId, organization.getId());

    CategoryDto categoryToUpdate = savedCategory;
    categoryToUpdate.setCode("Cat update");

    savedCategory = service.update(categoryToUpdate.getId(), categoryToUpdate, userId, organization.getId());

    assertNotNull(categoryToUpdate);
    assertNotNull(categoryToUpdate.getId());
    assertEquals(categoryToUpdate.getCode(), savedCategory.getCode());
    assertEquals(categoryToUpdate.getDesignation(), savedCategory.getDesignation());
    assertEquals(categoryToUpdate.getIdEntreprise(), savedCategory.getIdEntreprise());
  }

  @Test
  public void shouldThrowInvalidEntityException() {
    CategoryDto expectedCategory = CategoryDto.builder().build();
    // Phase 5c : l'utilisateur est autorise, pour que l'echec provienne bien de la VALIDATION et
    // non de la permission - sinon ce test ne verifierait plus ce qu'il annonce.
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Cat Invalid").active(true).build());
    Long userId = userWithAllWritePermissions(organization.getId());

    InvalidEntityException expectedException = assertThrows(InvalidEntityException.class,
        () -> service.save(expectedCategory, userId, organization.getId()));

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
        CategoryDto.builder().code(uniqueCode("CAT")).designation("Cat A").organization(orgA).build(),
        userWithAllWritePermissions(orgA.getId()), orgA.getId());

    assertEquals(categoryA.getId(), service.findById(categoryA.getId(), orgA.getId()).getId());
    assertEquals(categoryA.getCode(), service.findByCode(categoryA.getCode(), orgA.getId()).getCode());
    assertEquals(1, service.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> service.findById(categoryA.getId(), orgB.getId()));
    assertThrows(EntityNotFoundException.class, () -> service.findByCode(categoryA.getCode(), orgB.getId()));
    assertEquals(0, service.findAll(orgB.getId()).size());
  }

}
