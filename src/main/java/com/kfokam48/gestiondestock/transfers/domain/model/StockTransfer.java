package com.kfokam48.gestiondestock.transfers.domain.model;

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
@Table(name = "stock_transfer")
public class StockTransfer extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @ManyToOne
  @JoinColumn(name = "origin_site_id", nullable = false)
  private Site originSite;

  @ManyToOne
  @JoinColumn(name = "destination_site_id", nullable = false)
  private Site destinationSite;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private TransferStatus status = TransferStatus.BROUILLON;

  @Column(name = "requested_by_user_id", nullable = false)
  private Long requestedByUserId;

  @Column(name = "approved_by_user_id")
  private Long approvedByUserId;

  @Column(name = "request_date", nullable = false)
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
