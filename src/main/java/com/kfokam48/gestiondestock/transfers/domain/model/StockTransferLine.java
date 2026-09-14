package com.kfokam48.gestiondestock.transfers.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.model.AbstractEntity;
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
@Table(name = "stock_transfer_line")
public class StockTransferLine extends AbstractEntity {

  @ManyToOne
  @JoinColumn(name = "stock_transfer_id", nullable = false)
  private StockTransfer stockTransfer;

  @ManyToOne
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @Column(name = "quantite", nullable = false)
  private BigDecimal quantite;

}
