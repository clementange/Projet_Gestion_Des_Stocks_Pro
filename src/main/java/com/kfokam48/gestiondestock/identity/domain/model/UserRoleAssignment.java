package com.kfokam48.gestiondestock.identity.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Attribue un Role a un utilisateur (Utilisateur.id, hors module identity) pour un perimetre
 * donne. Role et Scope sont portes ensemble sur la meme affectation : un utilisateur peut avoir
 * plusieurs affectations (role + perimetre) distinctes.
 *
 * <p>Phase 3c : mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le
 * commentaire en tete de ce fichier XML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserRoleAssignment extends AbstractEntity {

  private Long userId;

  private Role role;

  private ScopeType scopeType;

  private Long scopeId;

}
