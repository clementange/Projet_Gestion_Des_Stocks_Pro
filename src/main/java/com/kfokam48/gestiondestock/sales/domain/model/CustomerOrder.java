package com.kfokam48.gestiondestock.sales.domain.model;

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

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @ManyToOne
  @JoinColumn(name = "site_id", nullable = false)
  private Site site;

  @Column(name = "order_date", nullable = false)
  private Instant orderDate;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private CustomerOrderStatus status = CustomerOrderStatus.BROUILLON;

  public void validate() {
    requireStatus(CustomerOrderStatus.BROUILLON, "validee");
    this.status = CustomerOrderStatus.VALIDEE;
  }

  public void requireReservable() {
    requireStatus(CustomerOrderStatus.VALIDEE, "reservee");
  }

  public void markReserved() {
    this.status = CustomerOrderStatus.RESERVEE;
  }

  public void requirePreparable() {
    requireStatus(CustomerOrderStatus.RESERVEE, "preparee");
  }

  public void markPrepared() {
    this.status = CustomerOrderStatus.PREPAREE;
  }

  public void requireShippable() {
    requireStatus(CustomerOrderStatus.PREPAREE, "expediee");
  }

  public void markShipped() {
    this.status = CustomerOrderStatus.EXPEDIEE;
  }

  public void requireDeliverable() {
    requireStatus(CustomerOrderStatus.EXPEDIEE, "livree");
  }

  public void markDelivered() {
    this.status = CustomerOrderStatus.LIVREE;
  }

  public void cancel() {
    if (status == CustomerOrderStatus.EXPEDIEE || status == CustomerOrderStatus.LIVREE || status == CustomerOrderStatus.ANNULEE) {
      throw new InvalidOperationException(
          "Impossible d'annuler une commande expediee, livree ou deja annulee (etat actuel : " + status + ")",
          ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION);
    }
    this.status = CustomerOrderStatus.ANNULEE;
  }

  private void requireStatus(CustomerOrderStatus expected, String targetTransitionLabel) {
    if (status != expected) {
      throw new InvalidOperationException(
          "La commande doit etre a l'etat " + expected + " pour etre " + targetTransitionLabel + " (etat actuel : " + status + ")",
          ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION);
    }
  }

}
