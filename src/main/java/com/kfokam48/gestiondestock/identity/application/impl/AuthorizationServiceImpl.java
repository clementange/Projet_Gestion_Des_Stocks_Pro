package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.domain.model.UserRoleAssignment;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class AuthorizationServiceImpl implements AuthorizationService {

  private UserRoleAssignmentRepository userRoleAssignmentRepository;

  @Autowired
  public AuthorizationServiceImpl(UserRoleAssignmentRepository userRoleAssignmentRepository) {
    this.userRoleAssignmentRepository = userRoleAssignmentRepository;
  }

  @Override
  public boolean hasPermission(Long userId, String permissionCode, ScopeType scopeType, Long scopeId, Long organizationId) {
    if (userId == null || permissionCode == null || scopeType == null || organizationId == null) {
      return false;
    }

    List<UserRoleAssignment> assignments = assignmentsForOrganization(userId, organizationId);

    return assignments.stream()
        .filter(assignment -> assignmentAppliesToScope(assignment, scopeType, scopeId))
        .anyMatch(assignment -> assignment.getRole().getPermissions().stream()
            .anyMatch(permission -> permissionCode.equals(permission.getCode())));
  }

  @Override
  public boolean hasGlobalAccess(Long userId, Long organizationId) {
    if (userId == null || organizationId == null) {
      return false;
    }
    return assignmentsForOrganization(userId, organizationId).stream()
        .anyMatch(assignment -> assignment.getScopeType() == ScopeType.GLOBAL);
  }

  // Phase 5b-1 : une affectation ScopeType.GLOBAL n'accorde plus la permission partout dans toute
  // l'application, seulement partout DANS SA PROPRE ORGANISATION - corrige le contournement
  // cross-tenant (n'importe quel administrateur de tenant, bootstrap en GLOBAL, pouvait auparavant
  // agir sur les ressources de n'importe quel autre tenant). Voir docs/phase-5b1-report.md.
  private List<UserRoleAssignment> assignmentsForOrganization(Long userId, Long organizationId) {
    return userRoleAssignmentRepository.findAllByUserId(userId).stream()
        .filter(assignment -> organizationId.equals(assignment.getOrganizationId()))
        .collect(Collectors.toList());
  }

  private boolean assignmentAppliesToScope(UserRoleAssignment assignment, ScopeType requestedScopeType, Long requestedScopeId) {
    if (assignment.getScopeType() == ScopeType.GLOBAL) {
      return true;
    }
    return assignment.getScopeType() == requestedScopeType
        && Objects.equals(assignment.getScopeId(), requestedScopeId);
  }
}
