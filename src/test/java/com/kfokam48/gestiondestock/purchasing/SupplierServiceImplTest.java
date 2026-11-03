package com.kfokam48.gestiondestock.purchasing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

@SpringBootTest
public class SupplierServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private SupplierService service;

  @Autowired
  private OrganizationService organizationService;

  // Phase 5c : voir le commentaire equivalent dans CustomerServiceImplTest.
  private OrganizationDto fixtureOrganization;
  private Long fixtureUser;

  @org.junit.jupiter.api.BeforeEach
  public void grantWritePermissions() {
    fixtureOrganization = organizationService.save(
        OrganizationDto.builder().name("Societe Fournisseur Test").active(true).build());
    fixtureUser = userWithAllWritePermissions(fixtureOrganization.getId());
  }

  @Test
  public void shouldSaveSupplierWithSuccess() {
    SupplierDto expected = SupplierDto.builder()
        .nom("Fournisseur Test")
        .prenom("Prenom Test")
        .mail("fournisseur.test@example.com")
        .numTel("+237600000002")
        .organizationId(fixtureOrganization.getId())
        .build();

    SupplierDto saved = service.save(expected, fixtureUser, fixtureOrganization.getId());

    assertNotNull(saved);
    assertNotNull(saved.getId());
    assertEquals(expected.getNom(), saved.getNom());
    assertEquals(expected.getMail(), saved.getMail());
  }

  @Test
  public void shouldThrowInvalidEntityException() {
    SupplierDto invalid = SupplierDto.builder().build();

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> service.save(invalid, fixtureUser, fixtureOrganization.getId()));

    assertEquals(ErrorCodes.SUPPLIER_NOT_VALID, exception.getErrorCode());
  }

  // Phase P1 : @Test(expected = ...) n'existe plus en JUnit 5. assertThrows est plus precis :
  // il cible l'instruction attendue en echec, au lieu d'accepter l'exception depuis
  // n'importe quel endroit de la methode - voir docs/phase-P1-report.md.
  @Test
  public void shouldThrowEntityNotFoundException() {
    assertThrows(EntityNotFoundException.class, () -> service.findById(0L));
  }

  @Test
  public void shouldDeleteSupplierWithSuccess() {
    SupplierDto saved = service.save(SupplierDto.builder().nom("Fournisseur A Supprimer")
        .organizationId(fixtureOrganization.getId()).build(), fixtureUser, fixtureOrganization.getId());

    service.delete(saved.getId());

    assertThrows(EntityNotFoundException.class, () -> service.findById(saved.getId()));
  }

  // Phase 5a : voir docs/phase-5a-report.md.
  @Test
  public void shouldRejectDuplicateMailOnCreate() {
    String mail = "dup-" + UUID.randomUUID() + "@test.local";
    service.save(SupplierDto.builder().nom("Premier").mail(mail)
        .organizationId(fixtureOrganization.getId()).build(), fixtureUser, fixtureOrganization.getId());

    InvalidEntityException exception = assertThrows(InvalidEntityException.class,
        () -> service.save(SupplierDto.builder().nom("Second").mail(mail)
            .organizationId(fixtureOrganization.getId()).build(), fixtureUser, fixtureOrganization.getId()));

    assertEquals(ErrorCodes.SUPPLIER_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  public void shouldAllowUpdatingSameSupplierWithoutMailConflict() {
    String mail = "self-" + UUID.randomUUID() + "@test.local";
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Fournisseur Update").active(true).build());
    Long userId = userWithAllWritePermissions(organization.getId());
    SupplierDto saved = service.save(SupplierDto.builder().nom("Fournisseur").mail(mail)
        .organizationId(organization.getId()).build(), userId, organization.getId());

    // Phase 5b-2g : voir le commentaire equivalent dans CustomerServiceImplTest.
    SupplierDto updated = service.update(saved.getId(),
        SupplierDto.builder().nom("Fournisseur Renomme").mail(mail).build(), userId, organization.getId());

    assertEquals("Fournisseur Renomme", updated.getNom());
  }

  // Phase 5b-2a : voir docs/phase-5b2a-report.md.
  @Test
  public void crossOrganizationReadsAreScoped() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe B").active(true).build());
    SupplierDto supplierA = service.save(
        SupplierDto.builder().nom("Fournisseur A").organizationId(orgA.getId()).build(),
        userWithAllWritePermissions(orgA.getId()), orgA.getId());

    assertEquals(supplierA.getId(), service.findById(supplierA.getId(), orgA.getId()).getId());
    assertEquals(1, service.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> service.findById(supplierA.getId(), orgB.getId()));
    assertEquals(0, service.findAll(orgB.getId()).size());
  }

}
