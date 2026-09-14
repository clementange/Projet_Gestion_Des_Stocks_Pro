package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.domain.model.UserRoleAssignment;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import java.util.List;
import java.util.Objects;
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
  public boolean hasPermission(Long userId, String permissionCode, ScopeType scopeType, Long scopeId) {
    if (userId == null || permissionCode == null || scopeType == null) {
      return false;
    }

    List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findAllByUserId(userId);

    return assignments.stream()
        .filter(assignment -> assignmentAppliesToScope(assignment, scopeType, scopeId))
        .anyMatch(assignment -> assignment.getRole().getPermissions().stream()
            .anyMatch(permission -> permissionCode.equals(permission.getCode())));
  }

  @Override
  public boolean hasGlobalAccess(Long userId) {
    if (userId == null) {
      return false;
    }
    return userRoleAssignmentRepository.findAllByUserId(userId).stream()
        .anyMatch(assignment -> assignment.getScopeType() == ScopeType.GLOBAL);
  }

  private boolean assignmentAppliesToScope(UserRoleAssignment assignment, ScopeType requestedScopeType, Long requestedScopeId) {
    if (assignment.getScopeType() == ScopeType.GLOBAL) {
      return true;
    }
    return assignment.getScopeType() == requestedScopeType
        && Objects.equals(assignment.getScopeId(), requestedScopeId);
  }
}
