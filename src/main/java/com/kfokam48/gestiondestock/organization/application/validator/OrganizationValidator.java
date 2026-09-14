package com.kfokam48.gestiondestock.organization.application.validator;

import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class OrganizationValidator {

  public static List<String> validate(OrganizationDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null || !StringUtils.hasLength(dto.getName())) {
      errors.add("Veuillez renseigner le nom de l'organisation");
    }
    return errors;
  }

}
