package com.kfokam48.gestiondestock.tenant.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.tenant.domain.model.Tenant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantDto {

  private Long id;

  private String nom;

  private String description;

  private AdresseDto adresse;

  private String codeFiscal;

  private String photo;

  private String email;

  private String numTel;

  private String steWeb;

  private Long organizationId;

  public static TenantDto fromEntity(Tenant tenant) {
    if (tenant == null) {
      return null;
    }
    return TenantDto.builder()
        .id(tenant.getId())
        .nom(tenant.getNom())
        .description(tenant.getDescription())
        .adresse(AdresseDto.fromEntity(tenant.getAdresse()))
        .codeFiscal(tenant.getCodeFiscal())
        .photo(tenant.getPhoto())
        .email(tenant.getEmail())
        .numTel(tenant.getNumTel())
        .steWeb(tenant.getSteWeb())
        .organizationId(tenant.getOrganizationId())
        .build();
  }

  public static Tenant toEntity(TenantDto dto) {
    if (dto == null) {
      return null;
    }
    Tenant tenant = new Tenant();
    tenant.setId(dto.getId());
    tenant.setNom(dto.getNom());
    tenant.setDescription(dto.getDescription());
    tenant.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    tenant.setCodeFiscal(dto.getCodeFiscal());
    tenant.setPhoto(dto.getPhoto());
    tenant.setEmail(dto.getEmail());
    tenant.setNumTel(dto.getNumTel());
    tenant.setSteWeb(dto.getSteWeb());
    tenant.setOrganizationId(dto.getOrganizationId());
    return tenant;
  }
}
