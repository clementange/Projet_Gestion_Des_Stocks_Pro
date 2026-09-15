package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.model.Adresse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Phase 3c (spike) : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le
// commentaire en tete de ce fichier XML pour la justification (Organization est referencee via
// @ManyToOne depuis catalog.Article/Category, son FQCN/package ne peut pas changer).
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Organization extends AbstractEntity {

  private String name;

  private String description;

  private boolean active = true;

  private String email;

  private String phone;

  private String website;

  private String taxCode;

  private String photo;

  private Adresse adresse;

}
