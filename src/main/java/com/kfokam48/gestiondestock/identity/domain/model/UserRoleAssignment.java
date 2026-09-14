package com.kfokam48.gestiondestock.identity.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import jakarta.persistence.Column;
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

/**
 * Attribue un Role a un utilisateur (Utilisateur.id, hors module identity) pour un perimetre
 * donne. Role et Scope sont portes ensemble sur la meme affectation : un utilisateur peut avoir
 * plusieurs affectations (role + perimetre) distinctes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "user_role_assignment")
public class UserRoleAssignment extends AbstractEntity {

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Column(name = "scope_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private ScopeType scopeType;

  @Column(name = "scope_id")
  private Long scopeId;

}
