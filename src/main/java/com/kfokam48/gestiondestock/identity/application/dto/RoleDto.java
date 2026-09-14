package com.kfokam48.gestiondestock.identity.application.dto;

import com.kfokam48.gestiondestock.identity.domain.model.Role;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleDto {

  private Long id;

  private String code;

  private String name;

  private String description;

  private Set<PermissionDto> permissions;

  public static RoleDto fromEntity(Role role) {
    if (role == null) {
      return null;
    }

    return RoleDto.builder()
        .id(role.getId())
        .code(role.getCode())
        .name(role.getName())
        .description(role.getDescription())
        .permissions(role.getPermissions().stream()
            .map(PermissionDto::fromEntity)
            .collect(Collectors.toSet()))
        .build();
  }

  /**
   * Ne resout PAS les permissions en entites gerees par Hibernate : une collection @ManyToMany a
   * besoin d'entites managees (ou d'un cascade) pour ecrire correctement la table de jointure.
   * C'est le role du service (RoleServiceImpl), qui recharge les Permission par ID avant de
   * sauvegarder.
   */
  public static Role toEntity(RoleDto dto) {
    if (dto == null) {
      return null;
    }

    Role role = new Role();
    role.setId(dto.getId());
    role.setCode(dto.getCode());
    role.setName(dto.getName());
    role.setDescription(dto.getDescription());

    return role;
  }
}
