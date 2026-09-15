package com.kfokam48.gestiondestock.sales.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
 * en tete de ce fichier XML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerOrderLine extends AbstractEntity {

  private CustomerOrder customerOrder;

  private Article article;

  private BigDecimal quantite;

  private BigDecimal prixUnitaire;

}
