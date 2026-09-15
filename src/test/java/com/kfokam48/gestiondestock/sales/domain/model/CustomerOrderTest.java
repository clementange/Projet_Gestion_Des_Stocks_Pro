package com.kfokam48.gestiondestock.sales.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

/**
 * Phase 3c/sales, etape 0 : golden master des invariants de {@link CustomerOrder}, ecrit et
 * verifie AVANT toute modification du mapping JPA. Test unitaire pur (aucun contexte Spring,
 * aucun @SpringBootTest), meme demarche que {@code inventory.domain.model.StockTest} (Phase
 * 3c/inventory) et {@code purchasing.domain.model.PurchaseOrderTest} (Phase 3c/purchasing).
 *
 * <p>CustomerOrder est la seule des 5 entites sales a porter des invariants metier non triviaux
 * (machine a etats complete BROUILLON -> VALIDEE -> RESERVEE -> PREPAREE -> EXPEDIEE -> LIVREE,
 * plus ANNULEE) : Customer, CustomerOrderLine, Sale et SaleLine sont des {@code @Data} purs, sans
 * methode de domaine, contrairement a l'hypothese de depart qui presumait aussi Sale. Ce test
 * doit passer, sans modification d'assertion, aussi bien avant qu'apres le decoupage
 * domaine/persistance de CustomerOrder.
 *
 * <p>CustomerOrder ne porte pas de {@code @Builder} Lombok (seulement {@code @Data}) :
 * instanciation directe via {@code new CustomerOrder()} + setter.
 */
class CustomerOrderTest {

  private static CustomerOrder orderWithStatus(CustomerOrderStatus status) {
    CustomerOrder order = new CustomerOrder();
    order.setStatus(status);
    return order;
  }

  // --- validate() : BROUILLON -> VALIDEE --------------------------------------------------------

  @Test
  void validateDepuisBrouillonPasseAValidee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.BROUILLON);
    order.validate();
    assertEquals(CustomerOrderStatus.VALIDEE, order.getStatus());
  }

  @Test
  void validateDepuisAutreEtatEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.RESERVEE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::validate);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("La commande doit etre a l'etat BROUILLON pour etre validee (etat actuel : RESERVEE)", ex.getMessage());
    assertEquals(CustomerOrderStatus.RESERVEE, order.getStatus());
  }

  // --- requireReservable() / markReserved() : VALIDEE -> RESERVEE --------------------------------

  @Test
  void requireReservableDepuisValideeNeLeveRien() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.VALIDEE);
    order.requireReservable();
    assertEquals(CustomerOrderStatus.VALIDEE, order.getStatus());
  }

  @Test
  void requireReservableDepuisAutreEtatEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.BROUILLON);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::requireReservable);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("La commande doit etre a l'etat VALIDEE pour etre reservee (etat actuel : BROUILLON)", ex.getMessage());
  }

  @Test
  void markReservedPasseAReservee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.VALIDEE);
    order.markReserved();
    assertEquals(CustomerOrderStatus.RESERVEE, order.getStatus());
  }

  // --- requirePreparable() / markPrepared() : RESERVEE -> PREPAREE -------------------------------

  @Test
  void requirePreparableDepuisReserveeNeLeveRien() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.RESERVEE);
    order.requirePreparable();
    assertEquals(CustomerOrderStatus.RESERVEE, order.getStatus());
  }

  @Test
  void requirePreparableDepuisAutreEtatEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.PREPAREE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::requirePreparable);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("La commande doit etre a l'etat RESERVEE pour etre preparee (etat actuel : PREPAREE)", ex.getMessage());
  }

  @Test
  void markPreparedPasseAPreparee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.RESERVEE);
    order.markPrepared();
    assertEquals(CustomerOrderStatus.PREPAREE, order.getStatus());
  }

  // --- requireShippable() / markShipped() : PREPAREE -> EXPEDIEE ---------------------------------

  @Test
  void requireShippableDepuisPrepareeNeLeveRien() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.PREPAREE);
    order.requireShippable();
    assertEquals(CustomerOrderStatus.PREPAREE, order.getStatus());
  }

  @Test
  void requireShippableDepuisAutreEtatEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.EXPEDIEE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::requireShippable);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("La commande doit etre a l'etat PREPAREE pour etre expediee (etat actuel : EXPEDIEE)", ex.getMessage());
  }

  @Test
  void markShippedPasseAExpediee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.PREPAREE);
    order.markShipped();
    assertEquals(CustomerOrderStatus.EXPEDIEE, order.getStatus());
  }

  // --- requireDeliverable() / markDelivered() : EXPEDIEE -> LIVREE -------------------------------

  @Test
  void requireDeliverableDepuisExpedieeNeLeveRien() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.EXPEDIEE);
    order.requireDeliverable();
    assertEquals(CustomerOrderStatus.EXPEDIEE, order.getStatus());
  }

  @Test
  void requireDeliverableDepuisAutreEtatEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.LIVREE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::requireDeliverable);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("La commande doit etre a l'etat EXPEDIEE pour etre livree (etat actuel : LIVREE)", ex.getMessage());
  }

  @Test
  void markDeliveredPasseALivree() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.EXPEDIEE);
    order.markDelivered();
    assertEquals(CustomerOrderStatus.LIVREE, order.getStatus());
  }

  // --- cancel() : autorise sauf EXPEDIEE/LIVREE/ANNULEE ------------------------------------------

  @Test
  void cancelDepuisBrouillonPasseAAnnulee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.BROUILLON);
    order.cancel();
    assertEquals(CustomerOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisValideePasseAAnnulee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.VALIDEE);
    order.cancel();
    assertEquals(CustomerOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisReserveePasseAAnnulee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.RESERVEE);
    order.cancel();
    assertEquals(CustomerOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisPrepareePasseAAnnulee() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.PREPAREE);
    order.cancel();
    assertEquals(CustomerOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisExpedieeEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.EXPEDIEE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::cancel);
    assertEquals(ErrorCodes.CUSTOMER_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Impossible d'annuler une commande expediee, livree ou deja annulee (etat actuel : EXPEDIEE)", ex.getMessage());
    assertEquals(CustomerOrderStatus.EXPEDIEE, order.getStatus());
  }

  @Test
  void cancelDepuisLivreeEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.LIVREE);
    assertThrows(InvalidOperationException.class, order::cancel);
    assertEquals(CustomerOrderStatus.LIVREE, order.getStatus());
  }

  @Test
  void cancelDepuisAnnuleeEstRejete() {
    CustomerOrder order = orderWithStatus(CustomerOrderStatus.ANNULEE);
    assertThrows(InvalidOperationException.class, order::cancel);
  }
}
