package com.kfokam48.gestiondestock.catalog.application.dto;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ArticleDto {

  private Long id;

  private String codeArticle;

  private String designation;

  private BigDecimal prixUnitaireHt;

  private BigDecimal tauxTva;

  private BigDecimal prixUnitaireTtc;

  private String photo;

  private CategoryDto category;

  private Long idEntreprise;

  private OrganizationDto organization;

  public static ArticleDto fromEntity(Article article) {
    if (article == null) {
      return null;
    }
    return ArticleDto.builder()
        .id(article.getId())
        .codeArticle(article.getCodeArticle())
        .designation(article.getDesignation())
        .photo(article.getPhoto())
        .prixUnitaireHt(article.getPrixUnitaireHt())
        .prixUnitaireTtc(article.getPrixUnitaireTtc())
        .tauxTva(article.getTauxTva())
        .idEntreprise(article.getIdEntreprise())
        .organization(OrganizationDto.fromEntity(article.getOrganization()))
        .category(CategoryDto.fromEntity(article.getCategory()))
        .build();
  }

  public static Article toEntity(ArticleDto articleDto) {
    if (articleDto == null) {
      return null;
    }
    Article article = new Article();
    article.setId(articleDto.getId());
    article.setCodeArticle(articleDto.getCodeArticle());
    article.setDesignation(articleDto.getDesignation());
    article.setPhoto(articleDto.getPhoto());
    article.setPrixUnitaireHt(articleDto.getPrixUnitaireHt());
    article.setPrixUnitaireTtc(articleDto.getPrixUnitaireTtc());
    article.setTauxTva(articleDto.getTauxTva());
    article.setIdEntreprise(articleDto.getIdEntreprise());
    article.setOrganization(OrganizationDto.toEntity(articleDto.getOrganization()));
    article.setCategory(CategoryDto.toEntity(articleDto.getCategory()));
    return article;
  }

}
