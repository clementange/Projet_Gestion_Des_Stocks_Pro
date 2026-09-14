package com.kfokam48.gestiondestock.sales.application.validator;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class CustomerOrderValidator {

  public static List<String> validate(CustomerOrderDto dto, List<CustomerOrderLineDto> lines) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner la commande client");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code de la commande");
    }
    if (dto.getCustomerId() == null) {
      errors.add("Veuillez renseigner le client");
    }
    if (dto.getSite() == null || dto.getSite().getId() == null) {
      errors.add("Veuillez renseigner le site de la commande");
    }
    if (lines == null || lines.isEmpty()) {
      errors.add("Une commande client doit comporter au moins une ligne");
    } else {
      for (CustomerOrderLineDto line : lines) {
        if (line.getArticle() == null || line.getArticle().getId() == null) {
          errors.add("Chaque ligne doit reference un article existant");
        }
        if (line.getQuantite() == null || line.getQuantite().compareTo(BigDecimal.ZERO) <= 0) {
          errors.add("La quantite commandee doit etre strictement positive");
        }
        if (line.getPrixUnitaire() == null || line.getPrixUnitaire().compareTo(BigDecimal.ZERO) < 0) {
          errors.add("Le prix unitaire doit etre renseigne et positif");
        }
      }
    }
    return errors;
  }

}
