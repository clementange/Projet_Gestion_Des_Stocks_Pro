package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * Phase 4a : le contrat HTTP {@code /utilisateurs/*} est servi par un adaptateur fin re-backe sur
 * identity.application.UserService, voir services.impl.UtilisateurServiceImpl. Champ
 * {@code roles} retire en Phase 4a : toujours serialise en liste vide (jamais alimente, aucun
 * RolesRepository n'a jamais existe pour le remplir), aucun test n'y touchait.
 *
 * <p>Phase 4b : {@code entreprise} est desormais un {@code tenant.application.dto.TenantDto}
 * (successeur de {@code dto.EntrepriseDto}, supprime) - meme forme JSON (mêmes noms de champs),
 * construit par UtilisateurServiceImpl via tenant.application.TenantService. Les anciennes
 * methodes {@code fromEntity}/{@code toEntity} (deja mortes depuis la Phase 4a) ont ete retirees :
 * elles ne pouvaient plus etre honnetement ecrites, model.Utilisateur.getEntreprise() retournant
 * model.Entreprise, un type distinct de tenant.domain.model.Tenant malgre la meme table physique
 * - voir docs/phase-4b-report.md.
 */
@Data
@Builder
public class UtilisateurDto {

  private Long id;

  private String nom;

  private String prenom;

  private String email;

  private Instant dateDeNaissance;

  private String moteDePasse;

  private AdresseDto adresse;

  private String photo;

  private TenantDto entreprise;

}
