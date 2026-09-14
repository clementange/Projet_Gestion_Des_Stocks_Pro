package com.kfokam48.gestiondestock.transfers.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.transfers.domain.model.StockTransferLine;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockTransferLineDto {

  private Long id;

  private Long stockTransferId;

  private ArticleDto article;

  private BigDecimal quantite;

  public static StockTransferLineDto fromEntity(StockTransferLine line) {
    if (line == null) {
      return null;
    }

    return StockTransferLineDto.builder()
        .id(line.getId())
        .stockTransferId(line.getStockTransfer() != null ? line.getStockTransfer().getId() : null)
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantite())
        .build();
  }

  public static StockTransferLine toNewEntity(StockTransferLineDto dto) {
    if (dto == null) {
      return null;
    }

    StockTransferLine line = new StockTransferLine();
    line.setArticle(ArticleDto.toEntity(dto.getArticle()));
    line.setQuantite(dto.getQuantite());

    return line;
  }
}
