package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.model.Adresse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
// en tete de ce fichier XML (Site est referencee via @ManyToOne depuis 6 autres modules, son
// FQCN/package ne peut pas changer).
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Site extends AbstractEntity {

  private String code;

  private String name;

  private SiteType type;

  private Adresse adresse;

  private boolean active = true;

  private City city;

}
