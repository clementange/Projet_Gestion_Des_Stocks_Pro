package com.kfokam48.gestiondestock.purchasing.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

/**
 * Phase 3c/purchasing, etape 0 : golden master des invariants de {@link PurchaseOrder}, ecrit et
 * verifie AVANT toute modification du mapping JPA. Test unitaire pur (aucun contexte Spring,
 * aucun @SpringBootTest) : jusqu'ici, ces invariants n'etaient exerces qu'indirectement par
 * PurchaseOrderServiceIntegrationTest. Ce fichier doit passer, sans modification d'assertion,
 * aussi bien avant qu'apres le decoupage domaine/persistance de PurchaseOrder.
 *
 * <p>PurchaseOrder ne porte pas de {@code @Builder} Lombok (seulement {@code @Data}) :
 * instanciation directe via {@code new PurchaseOrder()} + setters.
 */
class PurchaseOrderTest {

  private static PurchaseOrder orderWithStatus(PurchaseOrderStatus status) {
    PurchaseOrder order = new PurchaseOrder();
    order.setStatus(status);
    return order;
  }

  // --- validate() ------------------------------------------------------------------------------

  @Test
  void validateDepuisBrouillonPasseAValidee() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.BROUILLON);
    order.validate();
    assertEquals(PurchaseOrderStatus.VALIDEE, order.getStatus());
  }

  @Test
  void validateDepuisValideeEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.VALIDEE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::validate);
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Seule une commande a l'etat BROUILLON peut etre validee (etat actuel : VALIDEE)", ex.getMessage());
    assertEquals(PurchaseOrderStatus.VALIDEE, order.getStatus());
  }

  @Test
  void validateDepuisRecueEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.RECUE);
    assertThrows(InvalidOperationException.class, order::validate);
    assertEquals(PurchaseOrderStatus.RECUE, order.getStatus());
  }

  @Test
  void validateDepuisAnnuleeEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.ANNULEE);
    assertThrows(InvalidOperationException.class, order::validate);
  }

  // --- cancel() --------------------------------------------------------------------------------

  @Test
  void cancelDepuisBrouillonPasseAAnnulee() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.BROUILLON);
    order.cancel();
    assertEquals(PurchaseOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisValideePasseAAnnulee() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.VALIDEE);
    order.cancel();
    assertEquals(PurchaseOrderStatus.ANNULEE, order.getStatus());
  }

  @Test
  void cancelDepuisRecueEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.RECUE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::cancel);
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Impossible d'annuler une commande deja recue ou deja annulee (etat actuel : RECUE)", ex.getMessage());
    assertEquals(PurchaseOrderStatus.RECUE, order.getStatus());
  }

  @Test
  void cancelDepuisAnnuleeEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.ANNULEE);
    assertThrows(InvalidOperationException.class, order::cancel);
    assertEquals(PurchaseOrderStatus.ANNULEE, order.getStatus());
  }

  // --- requireReceivable() ----------------------------------------------------------------------

  @Test
  void requireReceivableDepuisValideeNeLeveRien() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.VALIDEE);
    order.requireReceivable();
    assertEquals(PurchaseOrderStatus.VALIDEE, order.getStatus());
  }

  @Test
  void requireReceivableDepuisBrouillonEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.BROUILLON);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, order::requireReceivable);
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Seule une commande VALIDEE peut etre receptionnee (etat actuel : BROUILLON)", ex.getMessage());
  }

  @Test
  void requireReceivableDepuisRecueEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.RECUE);
    assertThrows(InvalidOperationException.class, order::requireReceivable);
  }

  @Test
  void requireReceivableDepuisAnnuleeEstRejete() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.ANNULEE);
    assertThrows(InvalidOperationException.class, order::requireReceivable);
  }

  // --- markFullyReceived() ----------------------------------------------------------------------

  @Test
  void markFullyReceivedPasseAaRecue() {
    PurchaseOrder order = orderWithStatus(PurchaseOrderStatus.VALIDEE);
    order.markFullyReceived();
    assertEquals(PurchaseOrderStatus.RECUE, order.getStatus());
  }
}
