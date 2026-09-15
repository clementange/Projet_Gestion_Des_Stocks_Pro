package com.kfokam48.gestiondestock.purchasing.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
 * en tete de ce fichier XML. Aucune des methodes d'invariant ci-dessous ne porte d'annotation
 * JPA : ce deplacement ne les touche pas (golden master :
 * purchasing/domain/model/PurchaseOrderLineTest, verifie identique avant/apres).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrderLine extends AbstractEntity {

  private PurchaseOrder purchaseOrder;

  private Article article;

  private BigDecimal quantiteCommandee;

  private BigDecimal quantiteRecue = BigDecimal.ZERO;

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
