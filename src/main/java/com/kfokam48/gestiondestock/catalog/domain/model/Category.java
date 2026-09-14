package com.kfokam48.gestiondestock.catalog.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "category")
public class Category extends AbstractEntity {

  @Column(name = "code")
  private String code;

  @Column(name = "designation")
  private String designation;

  @Column(name = "identreprise")
  private Long idEntreprise;

  @ManyToOne
  @JoinColumn(name = "organization_id")
  private Organization organization;

  @OneToMany(mappedBy = "category")
  private List<Article> articles;

}
