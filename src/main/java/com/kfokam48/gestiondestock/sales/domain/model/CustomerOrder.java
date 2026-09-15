package com.kfokam48.gestiondestock.sales.domain.model;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
 * en tete de ce fichier XML. Aucune des methodes d'invariant ci-dessous ne porte d'annotation
 * JPA : ce deplacement ne les touche pas (golden master :
 * sales/domain/model/CustomerOrderTest, verifie identique avant/apres).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerOrder extends AbstractEntity {

  private String code;

  private Long customerId;

  private Site site;

  private Instant orderDate;

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
