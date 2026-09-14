package com.kfokam48.gestiondestock.organization.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SiteDto {

  private Long id;

  private String code;

  private String name;

  private SiteType type;

  private AdresseDto adresse;

  private boolean active;

  private CityDto city;

  public static SiteDto fromEntity(Site site) {
    if (site == null) {
      return null;
    }

    return SiteDto.builder()
        .id(site.getId())
        .code(site.getCode())
        .name(site.getName())
        .type(site.getType())
        .adresse(AdresseDto.fromEntity(site.getAdresse()))
        .active(site.isActive())
        .city(CityDto.fromEntity(site.getCity()))
        .build();
  }

  public static Site toEntity(SiteDto dto) {
    if (dto == null) {
      return null;
    }

    Site site = new Site();
    site.setId(dto.getId());
    site.setCode(dto.getCode());
    site.setName(dto.getName());
    site.setType(dto.getType());
    site.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    site.setActive(dto.isActive());
    site.setCity(CityDto.toEntity(dto.getCity()));

    return site;
  }
}
