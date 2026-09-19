package com.kfokam48.gestiondestock.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import java.util.List;
import org.junit.Test;

/**
 * Phase 5a : {@code AdresseValidator.validate} revalidait {@code adresse1} au lieu de
 * {@code codePostale} sur son dernier controle (copier-coller) - {@code codePostale} n'etait donc
 * jamais controle nulle part dans l'application (Client/Fournisseur avant Phase 4d,
 * TenantRegistrationValidator, UserValidator/UtilisateurValidator aujourd'hui). Voir
 * docs/phase-5a-report.md.
 */
public class AdresseValidatorTest {

  private static AdresseDto.AdresseDtoBuilder validAddress() {
    return AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000");
  }

  @Test
  public void missingCodePostaleIsNowRejected() {
    List<String> errors = AdresseValidator.validate(validAddress().codePostale(null).build());
    assertTrue(errors.stream().anyMatch(e -> e.contains("code postal")));
  }

  @Test
  public void emptyCodePostaleIsRejected() {
    // hasLength (pas hasText) est le contrat existant des 3 autres champs de cette classe -
    // une chaine blanche non-vide les passe deja tous, ce n'est pas le bug corrige ici.
    List<String> errors = AdresseValidator.validate(validAddress().codePostale("").build());
    assertTrue(errors.stream().anyMatch(e -> e.contains("code postal")));
  }

  @Test
  public void fullyValidAddressHasNoErrors() {
    assertTrue(AdresseValidator.validate(validAddress().build()).isEmpty());
  }

  @Test
  public void missingAdresse1IsStillRejectedIndependently() {
    List<String> errors = AdresseValidator.validate(validAddress().adresse1(null).build());
    assertTrue(errors.stream().anyMatch(e -> e.contains("adresse 1")));
    assertFalse(errors.stream().anyMatch(e -> e.contains("code postal")));
  }
}
