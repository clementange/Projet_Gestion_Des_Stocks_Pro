package com.kfokam48.gestiondestock.sales.application.dto;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.sales.domain.model.Sale;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaleDto {

  private Long id;

  private String code;

  private SiteDto site;

  private Instant saleDate;

  private String comment;

  public static SaleDto fromEntity(Sale sale) {
    if (sale == null) {
      return null;
    }

    return SaleDto.builder()
        .id(sale.getId())
        .code(sale.getCode())
        .site(SiteDto.fromEntity(sale.getSite()))
        .saleDate(sale.getSaleDate())
        .comment(sale.getComment())
        .build();
  }

  public static Sale toEntity(SaleDto dto) {
    if (dto == null) {
      return null;
    }

    Sale sale = new Sale();
    sale.setId(dto.getId());
    sale.setCode(dto.getCode());
    sale.setSite(SiteDto.toEntity(dto.getSite()));
    sale.setSaleDate(dto.getSaleDate());
    sale.setComment(dto.getComment());

    return sale;
  }
}
