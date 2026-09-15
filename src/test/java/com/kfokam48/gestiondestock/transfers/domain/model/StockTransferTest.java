package com.kfokam48.gestiondestock.transfers.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import org.junit.jupiter.api.Test;

/**
 * Phase 3c/transfers, etape 0 : golden master des invariants de {@link StockTransfer}, ecrit et
 * verifie AVANT toute modification du mapping JPA. Test unitaire pur (aucun contexte Spring,
 * aucun @SpringBootTest), meme demarche que {@code inventory.domain.model.StockTest} (Phase
 * 3c/inventory), {@code purchasing.domain.model.PurchaseOrderTest} (Phase 3c/purchasing) et
 * {@code sales.domain.model.CustomerOrderTest} (Phase 3c/sales).
 *
 * <p>StockTransfer est la seule des 2 entites transfers a porter des invariants metier non
 * triviaux (machine a etats complete BROUILLON -> DEMANDE -> APPROUVE -> EN_PREPARATION ->
 * EXPEDIE -> RECU, plus ANNULE) : StockTransferLine est un {@code @Data} pur, sans methode de
 * domaine, confirme par lecture directe avant d'ecrire le mapping (comme CustomerOrderLine/
 * SaleLine en Phase 3c/sales). Ce test doit passer, sans modification d'assertion, aussi bien
 * avant qu'apres le decoupage domaine/persistance de StockTransfer.
 *
 * <p>StockTransfer ne porte pas de {@code @Builder} Lombok (seulement {@code @Data}) :
 * instanciation directe via {@code new StockTransfer()} + setter.
 */
class StockTransferTest {

  private static StockTransfer transferWithStatus(TransferStatus status) {
    StockTransfer transfer = new StockTransfer();
    transfer.setStatus(status);
    return transfer;
  }

  // --- submit() : BROUILLON -> DEMANDE ------------------------------------------------------------

  @Test
  void submitDepuisBrouillonPasseADemande() {
    StockTransfer transfer = transferWithStatus(TransferStatus.BROUILLON);
    transfer.submit();
    assertEquals(TransferStatus.DEMANDE, transfer.getStatus());
  }

  @Test
  void submitDepuisAutreEtatEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.APPROUVE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, transfer::submit);
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Le transfert doit etre a l'etat BROUILLON pour etre demandee (etat actuel : APPROUVE)", ex.getMessage());
    assertEquals(TransferStatus.APPROUVE, transfer.getStatus());
  }

  // --- approve() : DEMANDE -> APPROUVE (memorise aussi approvedByUserId) -------------------------

  @Test
  void approveDepuisDemandePasseAApprouveEtMemoriseUtilisateur() {
    StockTransfer transfer = transferWithStatus(TransferStatus.DEMANDE);
    transfer.approve(42L);
    assertEquals(TransferStatus.APPROUVE, transfer.getStatus());
    assertEquals(42L, transfer.getApprovedByUserId());
  }

  @Test
  void approveDepuisAutreEtatEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.BROUILLON);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, () -> transfer.approve(42L));
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Le transfert doit etre a l'etat DEMANDE pour etre approuvee (etat actuel : BROUILLON)", ex.getMessage());
  }

  // --- requirePreparable() / markInPreparation() : APPROUVE -> EN_PREPARATION --------------------

  @Test
  void requirePreparableDepuisApprouveNeLeveRien() {
    StockTransfer transfer = transferWithStatus(TransferStatus.APPROUVE);
    transfer.requirePreparable();
    assertEquals(TransferStatus.APPROUVE, transfer.getStatus());
  }

  @Test
  void requirePreparableDepuisAutreEtatEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EN_PREPARATION);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, transfer::requirePreparable);
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Le transfert doit etre a l'etat APPROUVE pour etre mise en preparation (etat actuel : EN_PREPARATION)", ex.getMessage());
  }

  @Test
  void markInPreparationPasseAEnPreparation() {
    StockTransfer transfer = transferWithStatus(TransferStatus.APPROUVE);
    transfer.markInPreparation();
    assertEquals(TransferStatus.EN_PREPARATION, transfer.getStatus());
  }

  // --- requireShippable() / markShipped() : EN_PREPARATION -> EXPEDIE ----------------------------

  @Test
  void requireShippableDepuisEnPreparationNeLeveRien() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EN_PREPARATION);
    transfer.requireShippable();
    assertEquals(TransferStatus.EN_PREPARATION, transfer.getStatus());
  }

  @Test
  void requireShippableDepuisAutreEtatEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EXPEDIE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, transfer::requireShippable);
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Le transfert doit etre a l'etat EN_PREPARATION pour etre expediee (etat actuel : EXPEDIE)", ex.getMessage());
  }

  @Test
  void markShippedPasseAExpedie() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EN_PREPARATION);
    transfer.markShipped();
    assertEquals(TransferStatus.EXPEDIE, transfer.getStatus());
  }

  // --- requireReceivable() / markReceived() : EXPEDIE -> RECU -------------------------------------

  @Test
  void requireReceivableDepuisExpedieNeLeveRien() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EXPEDIE);
    transfer.requireReceivable();
    assertEquals(TransferStatus.EXPEDIE, transfer.getStatus());
  }

  @Test
  void requireReceivableDepuisAutreEtatEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.RECU);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, transfer::requireReceivable);
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Le transfert doit etre a l'etat EXPEDIE pour etre receptionnee (etat actuel : RECU)", ex.getMessage());
  }

  @Test
  void markReceivedPasseARecu() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EXPEDIE);
    transfer.markReceived();
    assertEquals(TransferStatus.RECU, transfer.getStatus());
  }

  // --- cancel() : autorise sauf EXPEDIE/RECU/ANNULE -----------------------------------------------

  @Test
  void cancelDepuisBrouillonPasseAAnnule() {
    StockTransfer transfer = transferWithStatus(TransferStatus.BROUILLON);
    transfer.cancel();
    assertEquals(TransferStatus.ANNULE, transfer.getStatus());
  }

  @Test
  void cancelDepuisDemandePasseAAnnule() {
    StockTransfer transfer = transferWithStatus(TransferStatus.DEMANDE);
    transfer.cancel();
    assertEquals(TransferStatus.ANNULE, transfer.getStatus());
  }

  @Test
  void cancelDepuisApprouvePasseAAnnule() {
    StockTransfer transfer = transferWithStatus(TransferStatus.APPROUVE);
    transfer.cancel();
    assertEquals(TransferStatus.ANNULE, transfer.getStatus());
  }

  @Test
  void cancelDepuisEnPreparationPasseAAnnule() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EN_PREPARATION);
    transfer.cancel();
    assertEquals(TransferStatus.ANNULE, transfer.getStatus());
  }

  @Test
  void cancelDepuisExpedieEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.EXPEDIE);
    InvalidOperationException ex = assertThrows(InvalidOperationException.class, transfer::cancel);
    assertEquals(ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION, ex.getErrorCode());
    assertEquals("Impossible d'annuler un transfert deja expedie, recu ou deja annule (etat actuel : EXPEDIE)", ex.getMessage());
    assertEquals(TransferStatus.EXPEDIE, transfer.getStatus());
  }

  @Test
  void cancelDepuisRecuEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.RECU);
    assertThrows(InvalidOperationException.class, transfer::cancel);
    assertEquals(TransferStatus.RECU, transfer.getStatus());
  }

  @Test
  void cancelDepuisAnnuleEstRejete() {
    StockTransfer transfer = transferWithStatus(TransferStatus.ANNULE);
    assertThrows(InvalidOperationException.class, transfer::cancel);
  }
}
