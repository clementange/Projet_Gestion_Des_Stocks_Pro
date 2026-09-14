package com.kfokam48.gestiondestock.identity.application.dto;

import com.kfokam48.gestiondestock.identity.domain.model.Permission;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionDto {

  private Long id;

  private String code;

  private String description;

  public static PermissionDto fromEntity(Permission permission) {
    if (permission == null) {
      return null;
    }

    return PermissionDto.builder()
        .id(permission.getId())
        .code(permission.getCode())
        .description(permission.getDescription())
        .build();
  }

  public static Permission toEntity(PermissionDto dto) {
    if (dto == null) {
      return null;
    }

    Permission permission = new Permission();
    permission.setId(dto.getId());
    permission.setCode(dto.getCode());
    permission.setDescription(dto.getDescription());

    return permission;
  }
}
