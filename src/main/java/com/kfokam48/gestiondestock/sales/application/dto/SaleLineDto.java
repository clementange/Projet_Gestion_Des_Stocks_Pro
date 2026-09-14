package com.kfokam48.gestiondestock.sales.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.sales.domain.model.SaleLine;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaleLineDto {

  private Long id;

  private Long saleId;

  private ArticleDto article;

  private BigDecimal quantite;

  private BigDecimal prixUnitaire;

  public static SaleLineDto fromEntity(SaleLine line) {
    if (line == null) {
      return null;
    }

    return SaleLineDto.builder()
        .id(line.getId())
        .saleId(line.getSale() != null ? line.getSale().getId() : null)
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  public static SaleLine toNewEntity(SaleLineDto dto) {
    if (dto == null) {
      return null;
    }

    SaleLine line = new SaleLine();
    line.setArticle(ArticleDto.toEntity(dto.getArticle()));
    line.setQuantite(dto.getQuantite());
    line.setPrixUnitaire(dto.getPrixUnitaire());

    return line;
  }
}
