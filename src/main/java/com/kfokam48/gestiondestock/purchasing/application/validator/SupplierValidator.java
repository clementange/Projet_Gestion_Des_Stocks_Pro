package com.kfokam48.gestiondestock.purchasing.application.validator;

import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class SupplierValidator {

  public static List<String> validate(SupplierDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null || !StringUtils.hasLength(dto.getNom())) {
      errors.add("Veuillez renseigner le nom du fournisseur");
    }
    return errors;
  }

}
