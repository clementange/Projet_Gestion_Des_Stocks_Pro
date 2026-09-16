package com.kfokam48.gestiondestock.tenant.application.validator;

import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.validator.AdresseValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Memes regles que {@code validator.EntrepriseValidator} (legacy) qu'il remplace - voir
 * docs/phase-4b-report.md. Valide uniquement les champs de l'entreprise ; les champs admin/mot de
 * passe sont valides separement par {@link TenantRegistrationValidator}.
 */
public class TenantValidator {

  public static List<String> validate(TenantDto dto) {
    List<String> errors = new ArrayList<>();
    if (dto == null) {
      errors.add("Veuillez renseigner le nom de l'entreprise");
      errors.add("Veuillez renseigner la description de l'entreprise");
      errors.add("Veuillez renseigner le code fiscal de l'entreprise");
      errors.add("Veuillez renseigner l'email de l'entreprise");
      errors.add("Veuillez renseigner le numero de telephone de l'entreprise");
      errors.addAll(AdresseValidator.validate(null));
      return errors;
    }

    if (!StringUtils.hasLength(dto.getNom())) {
      errors.add("Veuillez renseigner le nom de l'entreprise");
    }
    if (!StringUtils.hasLength(dto.getDescription())) {
      errors.add("Veuillez renseigner la description de l'entreprise");
    }
    if (!StringUtils.hasLength(dto.getCodeFiscal())) {
      errors.add("Veuillez renseigner le code fiscal de l'entreprise");
    }
    if (!StringUtils.hasLength(dto.getEmail())) {
      errors.add("Veuillez renseigner l'email de l'entreprise");
    }
    if (!StringUtils.hasLength(dto.getNumTel())) {
      errors.add("Veuillez renseigner le numero de telephone de l'entreprise");
    }
    errors.addAll(AdresseValidator.validate(dto.getAdresse()));
    return errors;
  }

}
