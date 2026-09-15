package com.kfokam48.gestiondestock.transfers.domain.model;

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
public class StockTransferLine extends AbstractEntity {

  private StockTransfer stockTransfer;

  private Article article;

  private BigDecimal quantite;

}
