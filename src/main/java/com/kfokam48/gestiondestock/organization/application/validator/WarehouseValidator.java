package com.kfokam48.gestiondestock.organization.application.validator;

import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

public class WarehouseValidator {

  /**
   * Ne verifie que la forme du DTO. L'invariant metier "le site doit etre de type ENTREPOT" ne
   * peut pas etre verifie ici : dto.getSite() est ce que le client a envoye dans la requete, pas
   * l'entite reellement persistee (un appelant HTTP legitime n'envoie generalement que
   * {"id": X} par reference, comme partout ailleurs dans cette API ; un appelant malveillant
   * pourrait au contraire mentir sur le type pour contourner l'invariant). Cette verification se
   * fait dans WarehouseServiceImpl, contre le Site reellement charge depuis la BDD.
   */
  public static List<String> validate(WarehouseDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner l'entrepot");
      return errors;
    }
    if (!StringUtils.hasLength(dto.getCode())) {
      errors.add("Veuillez renseigner le code de l'entrepot");
    }
    if (!StringUtils.hasLength(dto.getName())) {
      errors.add("Veuillez renseigner le nom de l'entrepot");
    }
    if (dto.getSite() == null || dto.getSite().getId() == null) {
      errors.add("Veuillez renseigner le site de l'entrepot");
    }
    return errors;
  }

}
