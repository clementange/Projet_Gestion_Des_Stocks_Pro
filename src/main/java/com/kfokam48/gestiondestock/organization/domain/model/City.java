package com.kfokam48.gestiondestock.organization.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "city")
public class City extends AbstractEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "country")
  private String country;

  @ManyToOne
  @JoinColumn(name = "organization_id", nullable = false)
  private Organization organization;

}
