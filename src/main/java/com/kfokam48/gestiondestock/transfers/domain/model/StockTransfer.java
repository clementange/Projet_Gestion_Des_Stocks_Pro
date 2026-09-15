package com.kfokam48.gestiondestock.transfers.domain.model;

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
 * transfers/domain/model/StockTransferTest, verifie identique avant/apres).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StockTransfer extends AbstractEntity {

  private String code;

  private Site originSite;

  private Site destinationSite;

  private TransferStatus status = TransferStatus.BROUILLON;

  private Long requestedByUserId;

  private Long approvedByUserId;

  private Instant requestDate;

  public void submit() {
    requireStatus(TransferStatus.BROUILLON, "demandee");
    this.status = TransferStatus.DEMANDE;
  }

  public void approve(Long userId) {
    requireStatus(TransferStatus.DEMANDE, "approuvee");
    this.approvedByUserId = userId;
    this.status = TransferStatus.APPROUVE;
  }

  public void requirePreparable() {
    requireStatus(TransferStatus.APPROUVE, "mise en preparation");
  }

  public void markInPreparation() {
    this.status = TransferStatus.EN_PREPARATION;
  }

  public void requireShippable() {
    requireStatus(TransferStatus.EN_PREPARATION, "expediee");
  }

  public void markShipped() {
    this.status = TransferStatus.EXPEDIE;
  }

  public void requireReceivable() {
    requireStatus(TransferStatus.EXPEDIE, "receptionnee");
  }

  public void markReceived() {
    this.status = TransferStatus.RECU;
  }

  public void cancel() {
    if (status == TransferStatus.EXPEDIE || status == TransferStatus.RECU || status == TransferStatus.ANNULE) {
      throw new InvalidOperationException(
          "Impossible d'annuler un transfert deja expedie, recu ou deja annule (etat actuel : " + status + ")",
          ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION);
    }
    this.status = TransferStatus.ANNULE;
  }

  private void requireStatus(TransferStatus expected, String targetTransitionLabel) {
    if (status != expected) {
      throw new InvalidOperationException(
          "Le transfert doit etre a l'etat " + expected + " pour etre " + targetTransitionLabel + " (etat actuel : " + status + ")",
          ErrorCodes.STOCK_TRANSFER_INVALID_TRANSITION);
    }
  }

}
