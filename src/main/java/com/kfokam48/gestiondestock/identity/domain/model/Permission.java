package com.kfokam48.gestiondestock.identity.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "permission")
public class Permission extends AbstractEntity {

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "description")
  private String description;

}
