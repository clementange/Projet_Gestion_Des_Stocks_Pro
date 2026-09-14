package com.kfokam48.gestiondestock.sales.application.validator;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class CustomerValidator {

  public static List<String> validate(CustomerDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null || !StringUtils.hasLength(dto.getNom())) {
      errors.add("Veuillez renseigner le nom du client");
    }
    return errors;
  }

}
