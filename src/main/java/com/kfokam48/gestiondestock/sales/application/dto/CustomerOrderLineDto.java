package com.kfokam48.gestiondestock.sales.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderLine;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerOrderLineDto {

  private Long id;

  private Long customerOrderId;

  private ArticleDto article;

  private BigDecimal quantite;

  private BigDecimal prixUnitaire;

  public static CustomerOrderLineDto fromEntity(CustomerOrderLine line) {
    if (line == null) {
      return null;
    }

    return CustomerOrderLineDto.builder()
        .id(line.getId())
        .customerOrderId(line.getCustomerOrder() != null ? line.getCustomerOrder().getId() : null)
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  public static CustomerOrderLine toNewEntity(CustomerOrderLineDto dto) {
    if (dto == null) {
      return null;
    }

    CustomerOrderLine line = new CustomerOrderLine();
    line.setArticle(ArticleDto.toEntity(dto.getArticle()));
    line.setQuantite(dto.getQuantite());
    line.setPrixUnitaire(dto.getPrixUnitaire());

    return line;
  }
}
