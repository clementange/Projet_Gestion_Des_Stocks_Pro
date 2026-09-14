package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.model.Adresse;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "site")
public class Site extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type", nullable = false)
  @Enumerated(EnumType.STRING)
  private SiteType type;

  @Embedded
  private Adresse adresse;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @ManyToOne
  @JoinColumn(name = "city_id", nullable = false)
  private City city;

}
