package com.kfokam48.gestiondestock.tenant.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.model.Adresse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 4b : successeur de {@code model.Entreprise}. Meme table physique ({@code entreprise})
 * reutilisee telle quelle - voir le commentaire en tete de META-INF/orm.xml.
 *
 * <p>{@code organizationId} reste un champ scalaire (pas de {@code @ManyToOne}) : c'est deja
 * ainsi dans {@code model.Entreprise} aujourd'hui (organization n'est referencee que par son ID),
 * pas une decision nouvelle de cet increment.
 *
 * <p>Aucun invariant metier : l'orchestration d'inscription (miroir Organization, site par
 * defaut, creation de l'utilisateur admin, amorcage RBAC) est une preoccupation applicative
 * multi-agregats, pas une regle portee par cet agregat seul - voir
 * tenant.application.TenantRegistrationService.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Tenant extends AbstractEntity {

  private String nom;

  private String description;

  private Adresse adresse;

  private String codeFiscal;

  private String photo;

  private String email;

  private String numTel;

  private String steWeb;

  private Long organizationId;

}
