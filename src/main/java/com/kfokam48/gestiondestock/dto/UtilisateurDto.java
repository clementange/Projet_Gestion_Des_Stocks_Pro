package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.model.Utilisateur;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * Phase 4a : {@code fromEntity}/{@code toEntity} ci-dessous ne sont plus appeles (le contrat HTTP
 * {@code /utilisateurs/*} est desormais servi par un adaptateur fin re-backe sur
 * identity.application.UserService, voir services.impl.UtilisateurServiceImpl) mais restent en
 * place pour ne pas toucher a model.Utilisateur/UtilisateurRepository, devenus orphelins - meme
 * etat que dto.CommandeFournisseurDto depuis la Phase 21. Champ {@code roles} retire : toujours
 * serialise en liste vide (jamais alimente, aucun RolesRepository n'a jamais existe pour le
 * remplir), aucun test n'y touchait : disparait du JSON plutot que de rester force a une liste
 * vide - voir docs/phase-4a-report.md.
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

  private EntrepriseDto entreprise;

  public static UtilisateurDto fromEntity(Utilisateur utilisateur) {
    if (utilisateur == null) {
      return null;
    }

    return UtilisateurDto.builder()
        .id(utilisateur.getId())
        .nom(utilisateur.getNom())
        .prenom(utilisateur.getPrenom())
        .email(utilisateur.getEmail())
        .moteDePasse(utilisateur.getMoteDePasse())
        .dateDeNaissance(utilisateur.getDateDeNaissance())
        .adresse(AdresseDto.fromEntity(utilisateur.getAdresse()))
        .photo(utilisateur.getPhoto())
        .entreprise(EntrepriseDto.fromEntity(utilisateur.getEntreprise()))
        .build();
  }

  public static Utilisateur toEntity(UtilisateurDto dto) {
    if (dto == null) {
      return null;
    }

    Utilisateur utilisateur = new Utilisateur();
    utilisateur.setId(dto.getId());
    utilisateur.setNom(dto.getNom());
    utilisateur.setPrenom(dto.getPrenom());
    utilisateur.setEmail(dto.getEmail());
    utilisateur.setMoteDePasse(dto.getMoteDePasse());
    utilisateur.setDateDeNaissance(dto.getDateDeNaissance());
    utilisateur.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    utilisateur.setPhoto(dto.getPhoto());
    utilisateur.setEntreprise(EntrepriseDto.toEntity(dto.getEntreprise()));

    return utilisateur;
  }
}
