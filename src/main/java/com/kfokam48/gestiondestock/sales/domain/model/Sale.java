package com.kfokam48.gestiondestock.sales.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Vente au comptant immediate (equivalent de l'ancien Ventes), rattachee a un site vendeur
 * (Livrable 6 : Ventes -> Site) et non a un client nomme, exactement comme le modele existant.
 * Contrairement a CustomerOrder, il n'y a pas de reservation : la sortie de stock est immediate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sale")
public class Sale extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @ManyToOne
  @JoinColumn(name = "site_id", nullable = false)
  private Site site;

  @Column(name = "sale_date", nullable = false)
  private Instant saleDate;

  @Column(name = "comment")
  private String comment;

}
