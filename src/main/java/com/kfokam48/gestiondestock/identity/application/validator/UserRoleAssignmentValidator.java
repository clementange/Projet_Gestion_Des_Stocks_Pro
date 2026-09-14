package com.kfokam48.gestiondestock.identity.application.validator;

import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import java.util.ArrayList;
import java.util.List;

public class UserRoleAssignmentValidator {

  public static List<String> validate(UserRoleAssignmentDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner l'affectation");
      return errors;
    }
    if (dto.getUserId() == null) {
      errors.add("Veuillez renseigner l'utilisateur");
    }
    if (dto.getRole() == null || dto.getRole().getId() == null) {
      errors.add("Veuillez renseigner le role");
    }
    if (dto.getScopeType() == null) {
      errors.add("Veuillez renseigner le type de perimetre (scope)");
    } else if (dto.getScopeType() == ScopeType.GLOBAL && dto.getScopeId() != null) {
      errors.add("Un perimetre GLOBAL ne doit pas avoir d'identifiant de ressource");
    } else if (dto.getScopeType() != ScopeType.GLOBAL && dto.getScopeId() == null) {
      errors.add("Veuillez renseigner l'identifiant de la ressource pour ce type de perimetre");
    }
    return errors;
  }

}
