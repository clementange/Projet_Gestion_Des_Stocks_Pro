package com.kfokam48.gestiondestock.purchasing.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
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
@Table(name = "purchase_order_line")
public class PurchaseOrderLine extends AbstractEntity {

  @ManyToOne
  @JoinColumn(name = "purchase_order_id", nullable = false)
  private PurchaseOrder purchaseOrder;

  @ManyToOne
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @Column(name = "quantite_commandee", nullable = false)
  private BigDecimal quantiteCommandee;

  @Column(name = "quantite_recue", nullable = false)
  private BigDecimal quantiteRecue = BigDecimal.ZERO;

  @Column(name = "prix_unitaire", nullable = false)
  private BigDecimal prixUnitaire;

  public BigDecimal getQuantiteRestanteARecevoir() {
    return quantiteCommandee.subtract(quantiteRecue);
  }

  public boolean isFullyReceived() {
    return getQuantiteRestanteARecevoir().compareTo(BigDecimal.ZERO) <= 0;
  }

  public void receive(BigDecimal quantity) {
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidOperationException("La quantite receptionnee doit etre strictement positive", ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION);
    }
    if (quantity.compareTo(getQuantiteRestanteARecevoir()) > 0) {
      throw new InvalidOperationException(
          "Impossible de receptionner " + quantity + " : il ne reste que " + getQuantiteRestanteARecevoir() + " a recevoir sur cette ligne",
          ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION);
    }
    this.quantiteRecue = this.quantiteRecue.add(quantity);
  }

}
