package com.kfokam48.gestiondestock.catalog.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import java.util.List;
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
public class Category extends AbstractEntity {

  private String code;

  private String designation;

  private Long idEntreprise;

  private Organization organization;

  private List<Article> articles;

}
