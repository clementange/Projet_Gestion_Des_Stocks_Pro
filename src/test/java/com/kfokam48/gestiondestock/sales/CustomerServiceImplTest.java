package com.kfokam48.gestiondestock.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CustomerServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private CustomerService service;

  @Test
  public void shouldSaveCustomerWithSuccess() {
    CustomerDto expected = CustomerDto.builder()
        .nom("Client Test")
        .prenom("Prenom Test")
        .mail("client.test@example.com")
        .numTel("+237600000001")
        .build();

    CustomerDto saved = service.save(expected);

    assertNotNull(saved);
    assertNotNull(saved.getId());
    assertEquals(expected.getNom(), saved.getNom());
    assertEquals(expected.getPrenom(), saved.getPrenom());
    assertEquals(expected.getMail(), saved.getMail());
  }

  @Test
  public void shouldThrowInvalidEntityException() {
    CustomerDto invalid = CustomerDto.builder().build();

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> service.save(invalid));

    assertEquals(ErrorCodes.CUSTOMER_NOT_VALID, exception.getErrorCode());
  }

  @Test(expected = EntityNotFoundException.class)
  public void shouldThrowEntityNotFoundException() {
    service.findById(0L);
  }

  @Test
  public void shouldDeleteCustomerWithSuccess() {
    CustomerDto saved = service.save(CustomerDto.builder().nom("Client A Supprimer").build());

    service.delete(saved.getId());

    assertThrows(EntityNotFoundException.class, () -> service.findById(saved.getId()));
  }

  // Phase 5a : voir docs/phase-5a-report.md.
  @Test
  public void shouldRejectDuplicateMailOnCreate() {
    String mail = "dup-" + UUID.randomUUID() + "@test.local";
    service.save(CustomerDto.builder().nom("Premier").mail(mail).build());

    InvalidEntityException exception = assertThrows(InvalidEntityException.class,
        () -> service.save(CustomerDto.builder().nom("Second").mail(mail).build()));

    assertEquals(ErrorCodes.CUSTOMER_ALREADY_EXISTS, exception.getErrorCode());
  }

  @Test
  public void shouldAllowUpdatingSameCustomerWithoutMailConflict() {
    String mail = "self-" + UUID.randomUUID() + "@test.local";
    CustomerDto saved = service.save(CustomerDto.builder().nom("Client").mail(mail).build());

    CustomerDto updated = service.save(CustomerDto.builder().id(saved.getId()).nom("Client Renomme").mail(mail).build());

    assertEquals("Client Renomme", updated.getNom());
  }

}
