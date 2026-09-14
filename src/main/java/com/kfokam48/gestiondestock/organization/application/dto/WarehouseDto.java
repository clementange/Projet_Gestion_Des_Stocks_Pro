package com.kfokam48.gestiondestock.organization.application.dto;

import com.kfokam48.gestiondestock.organization.domain.model.Warehouse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarehouseDto {

  private Long id;

  private String code;

  private String name;

  private String description;

  private boolean active;

  private SiteDto site;

  public static WarehouseDto fromEntity(Warehouse warehouse) {
    if (warehouse == null) {
      return null;
    }

    return WarehouseDto.builder()
        .id(warehouse.getId())
        .code(warehouse.getCode())
        .name(warehouse.getName())
        .description(warehouse.getDescription())
        .active(warehouse.isActive())
        .site(SiteDto.fromEntity(warehouse.getSite()))
        .build();
  }

  public static Warehouse toEntity(WarehouseDto dto) {
    if (dto == null) {
      return null;
    }

    Warehouse warehouse = new Warehouse();
    warehouse.setId(dto.getId());
    warehouse.setCode(dto.getCode());
    warehouse.setName(dto.getName());
    warehouse.setDescription(dto.getDescription());
    warehouse.setActive(dto.isActive());
    warehouse.setSite(SiteDto.toEntity(dto.getSite()));

    return warehouse;
  }
}
