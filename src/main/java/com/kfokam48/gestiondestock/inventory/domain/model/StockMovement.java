package com.kfokam48.gestiondestock.inventory.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Journal des mouvements de stock (evolution de l'ancien MvtStk, section 26) : chaque mutation
 * de Stock doit laisser une trace ici (QUOI, QUAND, COMBIEN, POURQUOI, SOURCE, UTILISATEUR,
 * SITE, ARTICLE, REFERENCE METIER).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "stock_movement")
public class StockMovement extends AbstractEntity {

  @ManyToOne
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @ManyToOne
  @JoinColumn(name = "site_id", nullable = false)
  private Site site;

  @Column(name = "date_mvt", nullable = false)
  private Instant dateMvt;

  @Column(name = "quantite", nullable = false)
  private BigDecimal quantite;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private StockMovementType type;

  @Column(name = "source", nullable = false)
  @Enumerated(EnumType.STRING)
  private StockMovementSource source;

  @Column(name = "reference")
  private String reference;

  @Column(name = "user_id")
  private Long userId;

}
