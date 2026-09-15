package com.kfokam48.gestiondestock.purchasing.domain.model;

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
 * Une commande fournisseur n'est jamais une reception (interdiction section 62). La creation et
 * la validation de cette commande ne touchent jamais au stock : seule la reception explicite
 * d'une ligne (PurchaseOrderService.receiveLine) appelle InventoryFacade.
 *
 * <p>Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le
 * commentaire en tete de ce fichier XML. Aucune des methodes d'invariant ci-dessous ne porte
 * d'annotation JPA : ce deplacement ne les touche pas (golden master :
 * purchasing/domain/model/PurchaseOrderTest, verifie identique avant/apres).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PurchaseOrder extends AbstractEntity {

  private String code;

  private Long supplierId;

  private Site site;

  private Instant orderDate;

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
