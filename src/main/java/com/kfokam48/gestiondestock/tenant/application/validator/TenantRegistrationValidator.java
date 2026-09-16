package com.kfokam48.gestiondestock.tenant.application.validator;

import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import com.kfokam48.gestiondestock.validator.AdresseValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Valide la requete d'inscription complete : champs entreprise (memes regles que
 * {@link TenantValidator}) + identite admin explicite + politique de mot de passe. Politique de
 * mot de passe nouvelle (n'existait pas avant : le mot de passe n'etait jamais fourni par
 * l'appelant) - 8 caracteres minimum + correspondance des deux champs, pas d'exigence de
 * complexite, choix delibere pour un outil interne - voir docs/phase-4b-report.md.
 */
public class TenantRegistrationValidator {

  private static final int MIN_PASSWORD_LENGTH = 8;

  public static List<String> validate(TenantRegistrationRequest request) {
    List<String> errors = new ArrayList<>();

    if (request == null) {
      errors.add("Veuillez renseigner le nom de l'entreprise");
      errors.add("Veuillez renseigner la description de l'entreprise");
      errors.add("Veuillez renseigner le code fiscal de l'entreprise");
      errors.add("Veuillez renseigner l'email de l'entreprise");
      errors.add("Veuillez renseigner le numero de telephone de l'entreprise");
      errors.add("Veuillez renseigner le nom de l'administrateur");
      errors.add("Veuillez renseigner le prenom de l'administrateur");
      errors.add("Veuillez renseigner l'email de l'administrateur");
      errors.add("Veuillez renseigner le mot de passe de l'administrateur");
      errors.addAll(AdresseValidator.validate(null));
      return errors;
    }

    if (!StringUtils.hasLength(request.nom())) {
      errors.add("Veuillez renseigner le nom de l'entreprise");
    }
    if (!StringUtils.hasLength(request.description())) {
      errors.add("Veuillez renseigner la description de l'entreprise");
    }
    if (!StringUtils.hasLength(request.codeFiscal())) {
      errors.add("Veuillez renseigner le code fiscal de l'entreprise");
    }
    if (!StringUtils.hasLength(request.email())) {
      errors.add("Veuillez renseigner l'email de l'entreprise");
    }
    if (!StringUtils.hasLength(request.numTel())) {
      errors.add("Veuillez renseigner le numero de telephone de l'entreprise");
    }
    errors.addAll(AdresseValidator.validate(request.adresse()));

    if (!StringUtils.hasLength(request.adminNom())) {
      errors.add("Veuillez renseigner le nom de l'administrateur");
    }
    if (!StringUtils.hasLength(request.adminPrenom())) {
      errors.add("Veuillez renseigner le prenom de l'administrateur");
    }
    if (!StringUtils.hasLength(request.adminEmail())) {
      errors.add("Veuillez renseigner l'email de l'administrateur");
    }
    if (request.adminDateDeNaissance() == null) {
      errors.add("Veuillez renseigner la date de naissance de l'administrateur");
    }

    if (!StringUtils.hasLength(request.motDePasse()) || !StringUtils.hasLength(request.confirmMotDePasse())) {
      errors.add("Veuillez renseigner le mot de passe et sa confirmation");
    } else {
      if (request.motDePasse().length() < MIN_PASSWORD_LENGTH) {
        errors.add("Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caracteres");
      }
      if (!request.motDePasse().equals(request.confirmMotDePasse())) {
        errors.add("Le mot de passe et sa confirmation ne correspondent pas");
      }
    }

    return errors;
  }

}
