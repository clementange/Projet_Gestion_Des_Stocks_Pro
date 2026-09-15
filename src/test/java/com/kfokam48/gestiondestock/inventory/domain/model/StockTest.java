package com.kfokam48.gestiondestock.inventory.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Phase 3c/inventory, etape 0 : golden master des invariants de {@link Stock}, ecrit et verifie
 * AVANT toute modification du mapping JPA. Test unitaire pur (aucun contexte Spring, aucun
 * @SpringBootTest) : jusqu'ici, ces invariants n'etaient exerces qu'indirectement par des tests
 * d'integration completes (InventoryFacadeIntegrationTest, CrossModuleConcurrencyIntegrationTest,
 * etc.) - un filet reel mais indirect. Ce fichier doit passer, sans modification d'assertion,
 * aussi bien avant qu'apres le decoupage domaine/persistance de Stock/StockMovement : c'est la
 * preuve que le comportement metier n'a pas bouge, independamment de ce que les tests
 * d'integration confirment par ailleurs.
 *
 * <p>Stock ne porte pas de {@code @Builder} Lombok (seulement {@code @Data}) : instanciation
 * directe via {@code new Stock()} + setters, pas via un builder qui n'existe pas.
 */
class StockTest {

  private static Stock stockOf(String physique, String reservee) {
    Stock stock = new Stock();
    stock.setQuantitePhysique(new BigDecimal(physique));
    stock.setQuantiteReservee(new BigDecimal(reservee));
    return stock;
  }

  // --- getQuantiteDisponible() -------------------------------------------------------------

  @Test
  void disponibleEstPhysiqueMoinsReservee() {
    Stock stock = stockOf("10", "3");
    assertEquals(0, new BigDecimal("7").compareTo(stock.getQuantiteDisponible()));
  }

  @Test
  void disponibleResteCoherentApresReserveEtLibererPartiel() {
    Stock stock = stockOf("10", "0");
    stock.reserve(new BigDecimal("6"));
    assertEquals(0, new BigDecimal("4").compareTo(stock.getQuantiteDisponible()));
    stock.releaseReservation(new BigDecimal("2"));
    assertEquals(0, new BigDecimal("6").compareTo(stock.getQuantiteDisponible()));
    assertEquals(0, new BigDecimal("10").compareTo(stock.getQuantitePhysique()));
    assertEquals(0, new BigDecimal("4").compareTo(stock.getQuantiteReservee()));
  }

  // --- receive() -----------------------------------------------------------------------------

  @Test
  void receiveNominalAugmenteLePhysique() {
    Stock stock = stockOf("10", "0");
    stock.receive(new BigDecimal("5"));
    assertEquals(0, new BigDecimal("15").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void receiveQuantiteZeroEstRejetee() {
    Stock stock = stockOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.receive(BigDecimal.ZERO));
    assertEquals(ErrorCodes.STOCK_MOVEMENT_NOT_VALID, ex.getErrorCode());
    assertEquals("La quantite doit etre strictement positive", ex.getMessage());
    assertEquals(0, new BigDecimal("10").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void receiveQuantiteNegativeEstRejetee() {
    Stock stock = stockOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.receive(new BigDecimal("-1")));
    assertEquals(ErrorCodes.STOCK_MOVEMENT_NOT_VALID, ex.getErrorCode());
  }

  @Test
  void receiveQuantiteNullEstRejetee() {
    Stock stock = stockOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.receive(null));
    assertEquals(ErrorCodes.STOCK_MOVEMENT_NOT_VALID, ex.getErrorCode());
  }

  // --- issue() -------------------------------------------------------------------------------

  @Test
  void issueNominalDiminueLePhysique() {
    Stock stock = stockOf("10", "0");
    stock.issue(new BigDecimal("4"));
    assertEquals(0, new BigDecimal("6").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void issueAuDelaDuDisponibleEstRejetee() {
    Stock stock = stockOf("5", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.issue(new BigDecimal("10")));
    assertEquals(ErrorCodes.STOCK_INSUFFICIENT, ex.getErrorCode());
    assertEquals("Stock disponible insuffisant : demande = 10, disponible = 5", ex.getMessage());
    assertEquals(0, new BigDecimal("5").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void issueTientCompteDuReserveDansLeDisponible() {
    // Physique = 10, dont 6 deja reservees : disponible = 4, donc issue(5) doit echouer meme si
    // le physique seul (10) suffirait.
    Stock stock = stockOf("10", "6");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.issue(new BigDecimal("5")));
    assertEquals(ErrorCodes.STOCK_INSUFFICIENT, ex.getErrorCode());
    assertEquals(0, new BigDecimal("10").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void issueQuantiteZeroOuNegativeEstRejetee() {
    Stock stock = stockOf("10", "0");
    assertThrows(InvalidOperationException.class, () -> stock.issue(BigDecimal.ZERO));
    assertThrows(InvalidOperationException.class, () -> stock.issue(new BigDecimal("-3")));
    assertThrows(InvalidOperationException.class, () -> stock.issue(null));
  }

  // --- reserve() -----------------------------------------------------------------------------

  @Test
  void reserveNominalAugmenteLaReservee() {
    Stock stock = stockOf("10", "0");
    stock.reserve(new BigDecimal("3"));
    assertEquals(0, new BigDecimal("3").compareTo(stock.getQuantiteReservee()));
    assertEquals(0, new BigDecimal("10").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void reserveAuDelaDuDisponibleEstRejetee() {
    Stock stock = stockOf("5", "2");
    // Disponible = 3, reserve(4) doit echouer.
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.reserve(new BigDecimal("4")));
    assertEquals(ErrorCodes.STOCK_INSUFFICIENT, ex.getErrorCode());
    assertEquals(0, new BigDecimal("2").compareTo(stock.getQuantiteReservee()));
  }

  @Test
  void reserveQuantiteZeroOuNegativeEstRejetee() {
    Stock stock = stockOf("10", "0");
    assertThrows(InvalidOperationException.class, () -> stock.reserve(BigDecimal.ZERO));
    assertThrows(InvalidOperationException.class, () -> stock.reserve(new BigDecimal("-1")));
  }

  // --- releaseReservation() -------------------------------------------------------------------

  @Test
  void releaseReservationNominalDiminueLaReservee() {
    Stock stock = stockOf("10", "5");
    stock.releaseReservation(new BigDecimal("2"));
    assertEquals(0, new BigDecimal("3").compareTo(stock.getQuantiteReservee()));
  }

  @Test
  void releaseReservationSuperieureALaReserveeEstRejetee() {
    Stock stock = stockOf("10", "2");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.releaseReservation(new BigDecimal("5")));
    assertEquals(ErrorCodes.STOCK_INSUFFICIENT, ex.getErrorCode());
    assertEquals("Impossible de liberer une reservation superieure a la quantite reellement reservee", ex.getMessage());
    assertEquals(0, new BigDecimal("2").compareTo(stock.getQuantiteReservee()));
  }

  @Test
  void releaseReservationQuantiteZeroOuNegativeEstRejetee() {
    Stock stock = stockOf("10", "5");
    assertThrows(InvalidOperationException.class, () -> stock.releaseReservation(BigDecimal.ZERO));
    assertThrows(InvalidOperationException.class, () -> stock.releaseReservation(new BigDecimal("-1")));
  }

  // --- correct() -----------------------------------------------------------------------------

  @Test
  void correctDeltaPositifAugmenteLePhysique() {
    Stock stock = stockOf("10", "0");
    stock.correct(new BigDecimal("5"));
    assertEquals(0, new BigDecimal("15").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void correctDeltaNegatifNominalDiminueLePhysique() {
    Stock stock = stockOf("10", "0");
    stock.correct(new BigDecimal("-4"));
    assertEquals(0, new BigDecimal("6").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void correctDeltaNegatifSuperieurAuPhysiqueEstRejete() {
    Stock stock = stockOf("3", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.correct(new BigDecimal("-10")));
    assertEquals(ErrorCodes.STOCK_INSUFFICIENT, ex.getErrorCode());
    assertEquals("Stock physique insuffisant pour cette correction : demande = 10, stock physique = 3", ex.getMessage());
    assertEquals(0, new BigDecimal("3").compareTo(stock.getQuantitePhysique()));
  }

  @Test
  void correctDeltaNullEstRejete() {
    Stock stock = stockOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.correct(null));
    assertEquals(ErrorCodes.STOCK_MOVEMENT_NOT_VALID, ex.getErrorCode());
    assertEquals("La quantite de correction ne peut pas etre nulle", ex.getMessage());
  }

  @Test
  void correctDeltaZeroEstRejete() {
    Stock stock = stockOf("10", "0");
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> stock.correct(BigDecimal.ZERO));
    assertEquals(ErrorCodes.STOCK_MOVEMENT_NOT_VALID, ex.getErrorCode());
    assertEquals("La quantite de correction ne peut pas etre nulle", ex.getMessage());
  }

  @Test
  void correctDeltaNegatifExactementEgalAuPhysiqueEstAccepte() {
    // Cas limite : correction negative qui vide exactement le stock physique (0 est autorise,
    // requireAvailablePhysical compare avec >= via compareTo < 0).
    Stock stock = stockOf("5", "0");
    stock.correct(new BigDecimal("-5"));
    assertEquals(0, BigDecimal.ZERO.compareTo(stock.getQuantitePhysique()));
  }
}
