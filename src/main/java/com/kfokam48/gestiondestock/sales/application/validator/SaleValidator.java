package com.kfokam48.gestiondestock.sales.application.validator;

import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class SaleValidator {

  public static List<String> validate(SaleDto dto, List<SaleLineDto> lines) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner la vente");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code de la vente");
    }
    if (dto.getSite() == null || dto.getSite().getId() == null) {
      errors.add("Veuillez renseigner le site de vente");
    }
    if (lines == null || lines.isEmpty()) {
      errors.add("Une vente doit comporter au moins une ligne");
    } else {
      for (SaleLineDto line : lines) {
        if (line.getArticle() == null || line.getArticle().getId() == null) {
          errors.add("Chaque ligne doit reference un article existant");
        }
        if (line.getQuantite() == null || line.getQuantite().compareTo(BigDecimal.ZERO) <= 0) {
          errors.add("La quantite vendue doit etre strictement positive");
        }
        if (line.getPrixUnitaire() == null || line.getPrixUnitaire().compareTo(BigDecimal.ZERO) < 0) {
          errors.add("Le prix unitaire doit etre renseigne et positif");
        }
      }
    }
    return errors;
  }

}
