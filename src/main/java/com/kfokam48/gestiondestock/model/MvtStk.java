package com.kfokam48.gestiondestock.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "mvtstk")
public class MvtStk extends AbstractEntity {

  @Column(name = "datemvt")
  private Instant dateMvt;

  @Column(name = "quantite")
  private BigDecimal quantite;

  @ManyToOne
  @JoinColumn(name = "idarticle")
  private Article article;

  @Column(name = "typemvt")
  @Enumerated(EnumType.STRING)
  private TypeMvtStk typeMvt;

  @Column(name = "sourcemvt")
  @Enumerated(EnumType.STRING)
  private SourceMvtStk sourceMvt;

  @Column(name = "identreprise")
  private Long idEntreprise;
}
