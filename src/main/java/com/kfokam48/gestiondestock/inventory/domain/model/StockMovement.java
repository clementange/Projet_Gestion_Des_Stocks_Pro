package com.kfokam48.gestiondestock.inventory.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
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
 *
 * <p>Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le
 * commentaire en tete de ce fichier XML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StockMovement extends AbstractEntity {

  private Article article;

  private Site site;

  private Instant dateMvt;

  private BigDecimal quantite;

  private StockMovementType type;

  private StockMovementSource source;

  private String reference;

  private Long userId;

}
