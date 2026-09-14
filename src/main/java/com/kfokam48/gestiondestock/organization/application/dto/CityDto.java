package com.kfokam48.gestiondestock.organization.application.dto;

import com.kfokam48.gestiondestock.organization.domain.model.City;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CityDto {

  private Long id;

  private String name;

  private String country;

  private OrganizationDto organization;

  public static CityDto fromEntity(City city) {
    if (city == null) {
      return null;
    }

    return CityDto.builder()
        .id(city.getId())
        .name(city.getName())
        .country(city.getCountry())
        .organization(OrganizationDto.fromEntity(city.getOrganization()))
        .build();
  }

  public static City toEntity(CityDto dto) {
    if (dto == null) {
      return null;
    }

    City city = new City();
    city.setId(dto.getId());
    city.setName(dto.getName());
    city.setCountry(dto.getCountry());
    city.setOrganization(OrganizationDto.toEntity(dto.getOrganization()));

    return city;
  }
}
