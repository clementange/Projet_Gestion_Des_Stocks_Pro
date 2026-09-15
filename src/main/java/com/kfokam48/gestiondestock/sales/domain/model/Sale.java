package com.kfokam48.gestiondestock.sales.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Vente au comptant immediate (equivalent de l'ancien Ventes), rattachee a un site vendeur
 * (Livrable 6 : Ventes -> Site) et non a un client nomme, exactement comme le modele existant.
 * Contrairement a CustomerOrder, il n'y a pas de reservation : la sortie de stock est immediate.
 *
 * <p>Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le
 * commentaire en tete de ce fichier XML. Contrairement a CustomerOrder, cette classe ne porte
 * aucun invariant metier (pas de methode de domaine) : simple porte-donnees.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Sale extends AbstractEntity {

  private String code;

  private Site site;

  private Instant saleDate;

  private String comment;

}
