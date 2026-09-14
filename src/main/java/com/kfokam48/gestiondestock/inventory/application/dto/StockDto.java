package com.kfokam48.gestiondestock.inventory.application.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.inventory.domain.model.Stock;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockDto {

  private Long id;

  private ArticleDto article;

  private SiteDto site;

  private BigDecimal quantitePhysique;

  private BigDecimal quantiteReservee;

  private BigDecimal quantiteDisponible;

  private BigDecimal seuilAlerte;

  public static StockDto fromEntity(Stock stock) {
    if (stock == null) {
      return null;
    }

    return StockDto.builder()
        .id(stock.getId())
        .article(ArticleDto.fromEntity(stock.getArticle()))
        .site(SiteDto.fromEntity(stock.getSite()))
        .quantitePhysique(stock.getQuantitePhysique())
        .quantiteReservee(stock.getQuantiteReservee())
        .quantiteDisponible(stock.getQuantiteDisponible())
        .seuilAlerte(stock.getSeuilAlerte())
        .build();
  }
}
