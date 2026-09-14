package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;

import com.kfokam48.gestiondestock.model.MvtStk;
import com.kfokam48.gestiondestock.model.SourceMvtStk;
import com.kfokam48.gestiondestock.model.TypeMvtStk;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MvtStkDto {

  private Long id;

  private Instant dateMvt;

  private BigDecimal quantite;

  private ArticleDto article;

  private TypeMvtStk typeMvt;

  private SourceMvtStk sourceMvt;

  private Long idEntreprise;

  public static MvtStkDto fromEntity(MvtStk mvtStk) {
    if (mvtStk == null) {
      return null;
    }

    return MvtStkDto.builder()
        .id(mvtStk.getId())
        .dateMvt(mvtStk.getDateMvt())
        .quantite(mvtStk.getQuantite())
        .article(ArticleDto.fromEntity(mvtStk.getArticle()))
        .typeMvt(mvtStk.getTypeMvt())
        .sourceMvt(mvtStk.getSourceMvt())
        .idEntreprise(mvtStk.getIdEntreprise())
        .build();
  }

  public static MvtStk toEntity(MvtStkDto dto) {
    if (dto == null) {
      return null;
    }

    MvtStk mvtStk = new MvtStk();
    mvtStk.setId(dto.getId());
    mvtStk.setDateMvt(dto.getDateMvt());
    mvtStk.setQuantite(dto.getQuantite());
    mvtStk.setArticle(ArticleDto.toEntity(dto.getArticle()));
    mvtStk.setTypeMvt(dto.getTypeMvt());
    mvtStk.setSourceMvt(dto.getSourceMvt());
    mvtStk.setIdEntreprise(dto.getIdEntreprise());
    return mvtStk;
  }
}
