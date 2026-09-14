package com.kfokam48.gestiondestock.inventory.domain.model;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Agregat metier : un stock est toujours rattache a un couple (article, site), jamais a un
 * article seul (interdiction section 62 : ne pas utiliser un stock global a la place du stock
 * par entrepot/site). Le site peut etre une boutique ou un entrepot (Livrable 4) : le lien est
 * volontairement vers Site, pas vers Warehouse, pour permettre le "stock local" d'une boutique
 * (section 9).
 *
 * <p>Toute mutation passe par une methode de ce domaine (receive/issue/reserve/
 * releaseReservation) qui protege les invariants : le stock physique ne peut jamais devenir
 * negatif, une reservation ne peut jamais depasser le disponible.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "stock", uniqueConstraints = @UniqueConstraint(columnNames = {"article_id", "site_id"}))
public class Stock extends AbstractEntity {

  @ManyToOne
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @ManyToOne
  @JoinColumn(name = "site_id", nullable = false)
  private Site site;

  @Column(name = "quantite_physique", nullable = false)
  private BigDecimal quantitePhysique = BigDecimal.ZERO;

  @Column(name = "quantite_reservee", nullable = false)
  private BigDecimal quantiteReservee = BigDecimal.ZERO;

  @Column(name = "seuil_alerte")
  private BigDecimal seuilAlerte;

  @Version
  @Column(name = "version")
  private Long version;

  public BigDecimal getQuantiteDisponible() {
    return quantitePhysique.subtract(quantiteReservee);
  }

  public void receive(BigDecimal quantity) {
    requirePositive(quantity);
    this.quantitePhysique = this.quantitePhysique.add(quantity);
  }

  public void issue(BigDecimal quantity) {
    requirePositive(quantity);
    requireAvailable(quantity);
    this.quantitePhysique = this.quantitePhysique.subtract(quantity);
  }

  public void reserve(BigDecimal quantity) {
    requirePositive(quantity);
    requireAvailable(quantity);
    this.quantiteReservee = this.quantiteReservee.add(quantity);
  }

  public void releaseReservation(BigDecimal quantity) {
    requirePositive(quantity);
    if (this.quantiteReservee.compareTo(quantity) < 0) {
      throw new InvalidOperationException(
          "Impossible de liberer une reservation superieure a la quantite reellement reservee",
          ErrorCodes.STOCK_INSUFFICIENT);
    }
    this.quantiteReservee = this.quantiteReservee.subtract(quantity);
  }

  public void correct(BigDecimal delta) {
    if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
      throw new InvalidOperationException("La quantite de correction ne peut pas etre nulle", ErrorCodes.STOCK_MOVEMENT_NOT_VALID);
    }
    if (delta.compareTo(BigDecimal.ZERO) > 0) {
      this.quantitePhysique = this.quantitePhysique.add(delta);
    } else {
      requireAvailablePhysical(delta.abs());
      this.quantitePhysique = this.quantitePhysique.add(delta);
    }
  }

  private void requirePositive(BigDecimal quantity) {
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidOperationException("La quantite doit etre strictement positive", ErrorCodes.STOCK_MOVEMENT_NOT_VALID);
    }
  }

  private void requireAvailable(BigDecimal quantity) {
    if (getQuantiteDisponible().compareTo(quantity) < 0) {
      throw new InvalidOperationException(
          "Stock disponible insuffisant : demande = " + quantity + ", disponible = " + getQuantiteDisponible(),
          ErrorCodes.STOCK_INSUFFICIENT);
    }
  }

  private void requireAvailablePhysical(BigDecimal quantity) {
    if (this.quantitePhysique.compareTo(quantity) < 0) {
      throw new InvalidOperationException(
          "Stock physique insuffisant pour cette correction : demande = " + quantity + ", stock physique = " + this.quantitePhysique,
          ErrorCodes.STOCK_INSUFFICIENT);
    }
  }
}
