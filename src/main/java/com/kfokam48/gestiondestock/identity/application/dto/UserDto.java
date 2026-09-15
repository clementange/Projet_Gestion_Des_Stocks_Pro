package com.kfokam48.gestiondestock.identity.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.identity.domain.model.User;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * {@code motDePasse} corrige (l'ancien {@code dto.UtilisateurDto.moteDePasse}, faute historique)
 * puisqu'il s'agit d'un contrat neuf sans frontend existant - voir docs/phase-4a-report.md.
 */
@Data
@Builder
public class UserDto {

  private Long id;

  private String nom;

  private String prenom;

  private String email;

  private Instant dateDeNaissance;

  private String motDePasse;

  private AdresseDto adresse;

  private String photo;

  private Long idEntreprise;

  public static UserDto fromEntity(User user) {
    if (user == null) {
      return null;
    }

    return UserDto.builder()
        .id(user.getId())
        .nom(user.getNom())
        .prenom(user.getPrenom())
        .email(user.getEmail())
        .dateDeNaissance(user.getDateDeNaissance())
        .motDePasse(user.getMotDePasse())
        .adresse(AdresseDto.fromEntity(user.getAdresse()))
        .photo(user.getPhoto())
        .idEntreprise(user.getIdEntreprise())
        .build();
  }

  public static User toEntity(UserDto dto) {
    if (dto == null) {
      return null;
    }

    User user = new User();
    user.setId(dto.getId());
    user.setNom(dto.getNom());
    user.setPrenom(dto.getPrenom());
    user.setEmail(dto.getEmail());
    user.setDateDeNaissance(dto.getDateDeNaissance());
    user.setMotDePasse(dto.getMotDePasse());
    user.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    user.setPhoto(dto.getPhoto());
    user.setIdEntreprise(dto.getIdEntreprise());

    return user;
  }
}
