package com.kfokam48.gestiondestock.identity.application.validator;

import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class PermissionValidator {

  public static List<String> validate(PermissionDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null || !StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code de la permission");
    }
    return errors;
  }

}
