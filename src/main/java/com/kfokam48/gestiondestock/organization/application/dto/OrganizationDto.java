package com.kfokam48.gestiondestock.organization.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizationDto {

  private Long id;

  private String name;

  private String description;

  private boolean active;

  private String email;

  private String phone;

  private String website;

  private String taxCode;

  private String photo;

  private AdresseDto adresse;

  public static OrganizationDto fromEntity(Organization organization) {
    if (organization == null) {
      return null;
    }

    return OrganizationDto.builder()
        .id(organization.getId())
        .name(organization.getName())
        .description(organization.getDescription())
        .active(organization.isActive())
        .email(organization.getEmail())
        .phone(organization.getPhone())
        .website(organization.getWebsite())
        .taxCode(organization.getTaxCode())
        .photo(organization.getPhoto())
        .adresse(AdresseDto.fromEntity(organization.getAdresse()))
        .build();
  }

  public static Organization toEntity(OrganizationDto dto) {
    if (dto == null) {
      return null;
    }

    Organization organization = new Organization();
    organization.setId(dto.getId());
    organization.setName(dto.getName());
    organization.setDescription(dto.getDescription());
    organization.setActive(dto.isActive());
    organization.setEmail(dto.getEmail());
    organization.setPhone(dto.getPhone());
    organization.setWebsite(dto.getWebsite());
    organization.setTaxCode(dto.getTaxCode());
    organization.setPhoto(dto.getPhoto());
    organization.setAdresse(AdresseDto.toEntity(dto.getAdresse()));

    return organization;
  }
}
