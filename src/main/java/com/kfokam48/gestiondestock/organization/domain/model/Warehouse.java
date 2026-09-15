package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
// en tete de ce fichier XML.
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Warehouse extends AbstractEntity {

  private String code;

  private String name;

  private String description;

  private boolean active = true;

  private Site site;

}
