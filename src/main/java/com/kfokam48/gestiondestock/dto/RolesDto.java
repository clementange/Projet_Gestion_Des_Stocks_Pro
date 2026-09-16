package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.model.Roles;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RolesDto {

  private Long id;

  private String roleName;

  @JsonIgnore
  private UtilisateurDto utilisateur;

  public static RolesDto fromEntity(Roles roles) {
    if (roles == null) {
      return null;
    }
    return RolesDto.builder()
        .id(roles.getId())
        .roleName(roles.getRoleName())
        .build();
  }

  // Phase 4b : UtilisateurDto.toEntity() a disparu (voir dto.UtilisateurDto) ; roles.utilisateur
  // n'est plus alimente ici. Sans consequence : RolesDto/model.Roles sont deja des le Phase 4a
  // confirmes totalement orphelins (aucun RolesRepository n'a jamais existe, cette methode n'est
  // jamais appelee) - voir docs/phase-4a-report.md et docs/phase-4b-report.md.
  public static Roles toEntity(RolesDto dto) {
    if (dto == null) {
      return null;
    }
    Roles roles = new Roles();
    roles.setId(dto.getId());
    roles.setRoleName(dto.getRoleName());
    return roles;
  }

}
