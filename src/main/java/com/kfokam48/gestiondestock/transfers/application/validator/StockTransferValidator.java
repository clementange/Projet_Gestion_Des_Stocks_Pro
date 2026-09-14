package com.kfokam48.gestiondestock.transfers.application.validator;

import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class StockTransferValidator {

  public static List<String> validate(StockTransferDto dto, List<StockTransferLineDto> lines) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner le transfert");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code du transfert");
    }
    if (dto.getRequestedByUserId() == null) {
      errors.add("Veuillez renseigner l'utilisateur initiateur du transfert");
    }
    if (dto.getOriginSite() == null || dto.getOriginSite().getId() == null) {
      errors.add("Veuillez renseigner le site d'origine");
    }
    if (dto.getDestinationSite() == null || dto.getDestinationSite().getId() == null) {
      errors.add("Veuillez renseigner le site de destination");
    }
    if (dto.getOriginSite() != null && dto.getDestinationSite() != null
        && Objects.equals(dto.getOriginSite().getId(), dto.getDestinationSite().getId())
        && dto.getOriginSite().getId() != null) {
      errors.add("Le site d'origine et le site de destination doivent etre differents");
    }
    if (lines == null || lines.isEmpty()) {
      errors.add("Un transfert doit comporter au moins une ligne");
    } else {
      for (StockTransferLineDto line : lines) {
        if (line.getArticle() == null || line.getArticle().getId() == null) {
          errors.add("Chaque ligne doit reference un article existant");
        }
        if (line.getQuantite() == null || line.getQuantite().compareTo(BigDecimal.ZERO) <= 0) {
          errors.add("La quantite transferee doit etre strictement positive");
        }
      }
    }
    return errors;
  }

}
