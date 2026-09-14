package com.kfokam48.gestiondestock.organization.application.validator;

import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class CityValidator {

  public static List<String> validate(CityDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner la ville");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getName())) {
      errors.add("Veuillez renseigner le nom de la ville");
    }
    if (dto.getOrganization() == null || dto.getOrganization().getId() == null) {
      errors.add("Veuillez renseigner l'organisation de la ville");
    }
    return errors;
  }

}
