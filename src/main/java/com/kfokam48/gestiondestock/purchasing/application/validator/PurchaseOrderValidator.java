package com.kfokam48.gestiondestock.purchasing.application.validator;

import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class PurchaseOrderValidator {

  public static List<String> validate(PurchaseOrderDto dto, List<PurchaseOrderLineDto> lines) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner la commande fournisseur");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code de la commande");
    }
    if (dto.getSupplierId() == null) {
      errors.add("Veuillez renseigner le fournisseur");
    }
    if (dto.getSite() == null || dto.getSite().getId() == null) {
      errors.add("Veuillez renseigner le site receptionnaire");
    }
    if (lines == null || lines.isEmpty()) {
      errors.add("Une commande fournisseur doit comporter au moins une ligne");
    } else {
      for (PurchaseOrderLineDto line : lines) {
        if (line.getArticle() == null || line.getArticle().getId() == null) {
          errors.add("Chaque ligne doit reference un article existant");
        }
        if (line.getQuantiteCommandee() == null || line.getQuantiteCommandee().compareTo(BigDecimal.ZERO) <= 0) {
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
