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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SupplierServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private SupplierService service;

  @Autowired
  private OrganizationService organizationService;

  @Test
  public void shouldSaveSupplierWithSuccess() {
    SupplierDto expected = SupplierDto.builder()
        .nom("Fournisseur Test")
        .prenom("Prenom Test")
        .mail("fournisseur.test@example.com")
        .numTel("+237600000002")
        .build();

    SupplierDto saved = service.save(expected);

    assertNotNull(saved);
    assertNotNull(saved.getId());
    assertEquals(expected.getNom(), saved.getNom());
    assertEquals(expected.getMail(), saved.getMail());
  }

  @Test
  public void shouldThrowInvalidEntityException() {
    SupplierDto invalid = SupplierDto.builder().build();

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> service.save(invalid));

    assertEquals(ErrorCodes.SUPPLIER_NOT_VALID, exception.getErrorCode());
  }

  @Test(expected = EntityNotFoundException.class)
  public void shouldThrowEntityNotFoundException() {
    service.findById(0L);
  }

  @Test
  public void shouldDeleteSupplierWithSuccess() {
    SupplierDto saved = service.save(SupplierDto.builder().nom("Fournisseur A Supprimer").build());

    service.delete(saved.getId());

    assertThrows(EntityNotFoundException.class, () -> service.findById(saved.getId()));
  }

  // Phase 5a : voir docs/phase-5a-report.md.
  @Test
  public void shouldRejectDuplicateMailOnCreate() {
    String mail = "dup-" + UUID.randomUUID() + "@test.local";
    service.save(SupplierDto.builder().nom("Premier").mail(mail).build());

    InvalidEntityException exception = assertThrows(InvalidEntityException.class,
        () -> service.save(SupplierDto.builder().nom("Second").mail(mail).build()));

    assertEquals(ErrorCodes.SUPPLIER_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  public void shouldAllowUpdatingSameSupplierWithoutMailConflict() {
    String mail = "self-" + UUID.randomUUID() + "@test.local";
    SupplierDto saved = service.save(SupplierDto.builder().nom("Fournisseur").mail(mail).build());

    SupplierDto updated = service.save(SupplierDto.builder().id(saved.getId()).nom("Fournisseur Renomme").mail(mail).build());

    assertEquals("Fournisseur Renomme", updated.getNom());
  }

  // Phase 5b-2a : voir docs/phase-5b2a-report.md.
  @Test
  public void crossOrganizationReadsAreScoped() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe B").active(true).build());
    SupplierDto supplierA = service.save(SupplierDto.builder().nom("Fournisseur A").organizationId(orgA.getId()).build());

    assertEquals(supplierA.getId(), service.findById(supplierA.getId(), orgA.getId()).getId());
    assertEquals(1, service.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> service.findById(supplierA.getId(), orgB.getId()));
    assertEquals(0, service.findAll(orgB.getId()).size());
  }

}
