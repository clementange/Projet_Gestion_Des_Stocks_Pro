package com.kfokam48.gestiondestock.catalog.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "article")
public class Article extends AbstractEntity {

  @Column(name = "codearticle")
  private String codeArticle;

  @Column(name = "designation")
  private String designation;

  @Column(name = "prixunitaireht")
  private BigDecimal prixUnitaireHt;

  @Column(name = "tauxtva")
  private BigDecimal tauxTva;

  @Column(name = "prixunitairettc")
  private BigDecimal prixUnitaireTtc;

  @Column(name = "photo")
  private String photo;

  @Column(name = "identreprise")
  private Long idEntreprise;

  @ManyToOne
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @ManyToOne
  @JoinColumn(name = "idcategory")
  private Category category;

}
