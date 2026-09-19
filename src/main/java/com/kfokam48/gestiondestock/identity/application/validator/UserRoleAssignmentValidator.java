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
    // Phase 5b-1 : impose au niveau applicatif (organization_id est nullable en base, comme
    // customer.organization_id/supplier.organization_id) - une affectation sans organisation ne
    // pourrait jamais etre prise en compte par AuthorizationServiceImpl.hasPermission, qui filtre
    // desormais par organisation avant toute autre verification. Voir docs/phase-5b1-report.md.
    if (dto.getOrganizationId() == null) {
      errors.add("Veuillez renseigner l'organisation de cette affectation");
    }
    return errors;
  }

}
