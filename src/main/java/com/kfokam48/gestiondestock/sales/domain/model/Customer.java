package com.kfokam48.gestiondestock.sales.domain.model;

import com.kfokam48.gestiondestock.shared.AbstractEntity;
import com.kfokam48.gestiondestock.shared.Adresse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
 * en tete de ce fichier XML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Customer extends AbstractEntity {

  private String nom;

  private String prenom;

  private Adresse adresse;

  private String photo;

  private String mail;

  private String numTel;

  private Long organizationId;

}
