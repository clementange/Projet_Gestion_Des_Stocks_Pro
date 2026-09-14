package com.kfokam48.gestiondestock.purchasing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SupplierServiceImplTest {

  @Autowired
  private SupplierService service;

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

}
