package com.kfokam48.gestiondestock.purchasing.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Phase 3c/purchasing, etape 0 : golden master des invariants de {@link PurchaseOrderLine}, ecrit
 * et verifie AVANT toute modification du mapping JPA. Test unitaire pur, meme demarche que
 * {@code PurchaseOrderTest} et {@code inventory.domain.model.StockTest} (Phase 3c/inventory).
 *
 * <p>PurchaseOrderLine ne porte pas de {@code @Builder} Lombok (seulement {@code @Data}) :
 * instanciation directe via {@code new PurchaseOrderLine()} + setters.
 */
class PurchaseOrderLineTest {

  private static PurchaseOrderLine lineOf(String commandee, String recue) {
    PurchaseOrderLine line = new PurchaseOrderLine();
    line.setQuantiteCommandee(new BigDecimal(commandee));
    line.setQuantiteRecue(new BigDecimal(recue));
    return line;
  }

  // --- getQuantiteRestanteARecevoir() / isFullyReceived() ---------------------------------------

  @Test
  void restanteARecevoirEstCommandeeMoinsRecue() {
    PurchaseOrderLine line = lineOf("10", "4");
    assertEquals(0, new BigDecimal("6").compareTo(line.getQuantiteRestanteARecevoir()));
    assertFalse(line.isFullyReceived());
  }

  @Test
  void isFullyReceivedEstVraiQuandRestanteEstNulleOuNegative() {
    PurchaseOrderLine complete = lineOf("10", "10");
    assertTrue(complete.isFullyReceived());

    PurchaseOrderLine surRecue = lineOf("10", "12");
    assertTrue(surRecue.isFullyReceived());
  }

  @Test
  void isFullyReceivedEstFauxTantQuIlResteADeLivrer() {
    PurchaseOrderLine line = lineOf("10", "9.99");
    assertFalse(line.isFullyReceived());
  }

  // --- receive() ----------------------------------------------------------------------------------

  @Test
  void receiveNominalAugmenteLaQuantiteRecue() {
    PurchaseOrderLine line = lineOf("10", "0");
    line.receive(new BigDecimal("4"));
    assertEquals(0, new BigDecimal("4").compareTo(line.getQuantiteRecue()));
  }

  @Test
  void receivePartielAccumule() {
    PurchaseOrderLine line = lineOf("10", "4");
    line.receive(new BigDecimal("6"));
    assertEquals(0, new BigDecimal("10").compareTo(line.getQuantiteRecue()));
    assertTrue(line.isFullyReceived());
  }

  @Test
  void receiveQuantiteZeroOuNegativeEstRejetee() {
    PurchaseOrderLine line = lineOf("10", "0");

    InvalidOperationException exZero = assertThrows(InvalidOperationException.class, () -> line.receive(BigDecimal.ZERO));
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, exZero.getErrorCode());
    assertEquals("La quantite receptionnee doit etre strictement positive", exZero.getMessage());

    assertThrows(InvalidOperationException.class, () -> line.receive(new BigDecimal("-1")));
    assertEquals(0, BigDecimal.ZERO.compareTo(line.getQuantiteRecue()));
  }

  @Test
  void receiveQuantiteNullEstRejetee() {
    PurchaseOrderLine line = lineOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> line.receive(null));
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, ex.getErrorCode());
  }

  @Test
  void receiveAuDelaDuRestantEstRejetee() {
    PurchaseOrderLine line = lineOf("10", "4");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> line.receive(new BigDecimal("7")));
    assertEquals(ErrorCodes.PURCHASE_ORDER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Impossible de receptionner 7 : il ne reste que 6 a recevoir sur cette ligne", ex.getMessage());
    assertEquals(0, new BigDecimal("4").compareTo(line.getQuantiteRecue()));
  }

  @Test
  void receiveExactementLeRestantEstAccepte() {
    PurchaseOrderLine line = lineOf("10", "4");
    line.receive(new BigDecimal("6"));
    assertTrue(line.isFullyReceived());
  }
}
