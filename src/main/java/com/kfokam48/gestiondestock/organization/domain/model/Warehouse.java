package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "warehouse")
public class Warehouse extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @OneToOne
  @JoinColumn(name = "site_id", nullable = false, unique = true)
  private Site site;

}
