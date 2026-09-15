package com.kfokam48.gestiondestock.identity.application.validator;

import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.validator.AdresseValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Meme regles que {@code validator.UtilisateurValidator} (legacy) qu'il remplace - voir
 * docs/phase-4a-report.md.
 */
public class UserValidator {

  public static List<String> validate(UserDto dto) {
    List<String> errors = new ArrayList<>();

    if (dto == null) {
      errors.add("Veuillez renseigner le nom d'utilisateur");
      errors.add("Veuillez renseigner le prenom d'utilisateur");
      errors.add("Veuillez renseigner le mot de passe d'utilisateur");
      errors.add("Veuillez renseigner l'adresse d'utilisateur");
      errors.addAll(AdresseValidator.validate(null));
      return errors;
    }

    if (!StringUtils.hasLength(dto.getNom())) {
      errors.add("Veuillez renseigner le nom d'utilisateur");
    }
    if (!StringUtils.hasLength(dto.getPrenom())) {
      errors.add("Veuillez renseigner le prenom d'utilisateur");
    }
    if (!StringUtils.hasLength(dto.getEmail())) {
      errors.add("Veuillez renseigner l'email d'utilisateur");
    }
    if (!StringUtils.hasLength(dto.getMotDePasse())) {
      errors.add("Veuillez renseigner le mot de passe d'utilisateur");
    }
    if (dto.getDateDeNaissance() == null) {
      errors.add("Veuillez renseigner la date de naissance d'utilisateur");
    }
    errors.addAll(AdresseValidator.validate(dto.getAdresse()));

    return errors;
  }

}
