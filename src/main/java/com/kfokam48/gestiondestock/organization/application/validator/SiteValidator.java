package com.kfokam48.gestiondestock.organization.application.validator;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class SiteValidator {

  public static List<String> validate(SiteDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner le site");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code du site");
    }
    if (!StringUtils.hasLength(dto.getName())) {
      errors.add("Veuillez renseigner le nom du site");
    }
    if (dto.getType() == null) {
      errors.add("Veuillez renseigner le type du site (BOUTIQUE, AGENCE ou ENTREPOT)");
    }
    if (dto.getCity() == null || dto.getCity().getId() == null) {
      errors.add("Veuillez renseigner la ville du site");
    }
    return errors;
  }

}
