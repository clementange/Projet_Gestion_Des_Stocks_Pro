package com.kfokam48.gestiondestock.identity.application.dto;

import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.domain.model.UserRoleAssignment;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRoleAssignmentDto {

  private Long id;

  private Long userId;

  private RoleDto role;

  private ScopeType scopeType;

  private Long scopeId;

  private Long organizationId;

  public static UserRoleAssignmentDto fromEntity(UserRoleAssignment assignment) {
    if (assignment == null) {
      return null;
    }

    return UserRoleAssignmentDto.builder()
        .id(assignment.getId())
        .userId(assignment.getUserId())
        .role(RoleDto.fromEntity(assignment.getRole()))
        .scopeType(assignment.getScopeType())
        .scopeId(assignment.getScopeId())
        .organizationId(assignment.getOrganizationId())
        .build();
  }

  public static UserRoleAssignment toEntity(UserRoleAssignmentDto dto) {
    if (dto == null) {
      return null;
    }

    UserRoleAssignment assignment = new UserRoleAssignment();
    assignment.setId(dto.getId());
    assignment.setUserId(dto.getUserId());
    assignment.setRole(RoleDto.toEntity(dto.getRole()));
    assignment.setScopeType(dto.getScopeType());
    assignment.setScopeId(dto.getScopeId());
    assignment.setOrganizationId(dto.getOrganizationId());

    return assignment;
  }
}
