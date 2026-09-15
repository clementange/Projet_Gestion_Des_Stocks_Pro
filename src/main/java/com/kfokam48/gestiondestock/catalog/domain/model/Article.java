package com.kfokam48.gestiondestock.catalog.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

// Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire
// en tete de ce fichier XML (Article est referencee via @ManyToOne depuis inventory, purchasing,
// sales, transfers + 4 entites legacy, son FQCN/package ne peut pas changer).
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Article extends AbstractEntity {

  private String codeArticle;

  private String designation;

  private BigDecimal prixUnitaireHt;

  private BigDecimal tauxTva;

  private BigDecimal prixUnitaireTtc;

  private String photo;

  private Long idEntreprise;

  private Organization organization;

  private Category category;

}
