package com.kfokam48.gestiondestock.sales.domain.model;

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
@Table(name = "customer_order_line")
public class CustomerOrderLine extends AbstractEntity {

  @ManyToOne
  @JoinColumn(name = "customer_order_id", nullable = false)
  private CustomerOrder customerOrder;

  @ManyToOne
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @Column(name = "quantite", nullable = false)
  private BigDecimal quantite;

  @Column(name = "prix_unitaire", nullable = false)
  private BigDecimal prixUnitaire;

}
