package com.kfokam48.gestiondestock.inventory.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovement;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementType;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockMovementDto {

  private Long id;

  private ArticleDto article;

  private SiteDto site;

  private Instant dateMvt;

  private BigDecimal quantite;

  private StockMovementType type;

  private StockMovementSource source;

  private String reference;

  private Long userId;

  public static StockMovementDto fromEntity(StockMovement movement) {
    if (movement == null) {
      return null;
    }

    return StockMovementDto.builder()
        .id(movement.getId())
        .article(ArticleDto.fromEntity(movement.getArticle()))
        .site(SiteDto.fromEntity(movement.getSite()))
        .dateMvt(movement.getDateMvt())
        .quantite(movement.getQuantite())
        .type(movement.getType())
        .source(movement.getSource())
        .reference(movement.getReference())
        .userId(movement.getUserId())
        .build();
  }
}
