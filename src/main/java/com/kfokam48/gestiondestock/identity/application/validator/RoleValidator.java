package com.kfokam48.gestiondestock.identity.application.validator;

import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class RoleValidator {

  public static List<String> validate(RoleDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner le role");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code du role");
    }
    if (!StringUtils.hasLength(dto.getName())) {
      errors.add("Veuillez renseigner le nom du role");
    }
    return errors;
  }

}
