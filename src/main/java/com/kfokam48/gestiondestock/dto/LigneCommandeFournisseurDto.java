package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;

import com.kfokam48.gestiondestock.model.CommandeFournisseur;
import com.kfokam48.gestiondestock.model.LigneCommandeFournisseur;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LigneCommandeFournisseurDto {

  private Long id;

  private ArticleDto article;

  private CommandeFournisseur commandeFournisseur;

  private BigDecimal quantite;

  private BigDecimal prixUnitaire;

  private Long idEntreprise;

  public static LigneCommandeFournisseurDto fromEntity(LigneCommandeFournisseur ligneCommandeFournisseur) {
    if (ligneCommandeFournisseur == null) {
      return null;
    }
    return LigneCommandeFournisseurDto.builder()
        .id(ligneCommandeFournisseur.getId())
        .article(ArticleDto.fromEntity(ligneCommandeFournisseur.getArticle()))
        .quantite(ligneCommandeFournisseur.getQuantite())
        .prixUnitaire(ligneCommandeFournisseur.getPrixUnitaire())
        .idEntreprise(ligneCommandeFournisseur.getIdEntreprise())
        .build();
  }

  public static LigneCommandeFournisseur toEntity(LigneCommandeFournisseurDto dto) {
    if (dto == null) {
      return null;
    }

    LigneCommandeFournisseur ligneCommandeFournisseur = new LigneCommandeFournisseur();
    ligneCommandeFournisseur.setId(dto.getId());
    ligneCommandeFournisseur.setArticle(ArticleDto.toEntity(dto.getArticle()));
    ligneCommandeFournisseur.setPrixUnitaire(dto.getPrixUnitaire());
    ligneCommandeFournisseur.setQuantite(dto.getQuantite());
    ligneCommandeFournisseur.setIdEntreprise(dto.getIdEntreprise());
    return ligneCommandeFournisseur;
  }

}
