package com.kfokam48.gestiondestock.purchasing.domain.model;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Une commande fournisseur n'est jamais une reception (interdiction section 62). La creation et
 * la validation de cette commande ne touchent jamais au stock : seule la reception explicite
 * d'une ligne (PurchaseOrderService.receiveLine) appelle InventoryFacade.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @ManyToOne
  @JoinColumn(name = "site_id", nullable = false)
  private Site site;

  @Column(name = "order_date", nullable = false)
  private Instant orderDate;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private PurchaseOrderStatus status = PurchaseOrderStatus.BROUILLON;

  public void validate() {
    if (status != PurchaseOrderStatus.BROUILLON) {
      throw new InvalidOperationException(
          "Seule une commande a l'etat BROUILLON peut etre validee (etat actuel : " + status + ")",
          ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION);
    }
    this.status = PurchaseOrderStatus.VALIDEE;
  }

  public void cancel() {
    if (status == PurchaseOrderStatus.RECUE || status == PurchaseOrderStatus.ANNULEE) {
      throw new InvalidOperationException(
          "Impossible d'annuler une commande deja recue ou deja annulee (etat actuel : " + status + ")",
          ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION);
    }
    this.status = PurchaseOrderStatus.ANNULEE;
  }

  public void requireReceivable() {
    if (status != PurchaseOrderStatus.VALIDEE) {
      throw new InvalidOperationException(
          "Seule une commande VALIDEE peut etre receptionnee (etat actuel : " + status + ")",
          ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION);
    }
  }

  public void markFullyReceived() {
    this.status = PurchaseOrderStatus.RECUE;
  }

}
