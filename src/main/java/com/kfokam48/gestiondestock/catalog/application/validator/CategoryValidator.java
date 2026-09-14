package com.kfokam48.gestiondestock.catalog.application.validator;

import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class CategoryValidator {

  public static List<String> validate(CategoryDto categoryDto) {
    List<String> errors = new ArrayList<>();

    if (categoryDto == null || !StringUtils.hasLength(categoryDto.getCode())) {
      errors.add("Veuillez renseigner le code de la categorie");
    }
    return errors;
  }

}
