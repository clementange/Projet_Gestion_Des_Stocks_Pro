package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.services.UtilisateurService;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 4a : /utilisateurs/* garde son contrat HTTP (UtilisateurDto, y compris la faute
// historique "moteDePasse") mais est desormais re-backe par identity.User/UserService, comme
// FournisseurServiceImpl est re-backe par purchasing.Supplier depuis la Phase 18. Les codes
// d'erreur legacy (UTILISATEUR_NOT_FOUND/UTILISATEUR_ALREADY_EXISTS) sont preserves explicitement
// (contrairement a Fournisseur/Client, dont les 404 laissent deja fuiter le code du module neuf,
// SUPPLIER_NOT_FOUND/CUSTOMER_NOT_FOUND — comportement deja characterise avant cette migration,
// donc deja "actuel" a l'epoque ou ces adapters ont ete ecrits) : ici la migration se fait
// maintenant, donc le comportement observable AVANT ce commit (UTILISATEUR_*) doit rester
// identique APRES, voir docs/phase-4a-report.md et
// UtilisateurAuthenticationCharacterizationTest.
@Service
@Slf4j
public class UtilisateurServiceImpl implements UtilisateurService {

  private UserService userService;
  private TenantService tenantService;

  @Autowired
  public UtilisateurServiceImpl(UserService userService, TenantService tenantService) {
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @Override
  public UtilisateurDto save(UtilisateurDto dto) {
    try {
      UserDto saved = userService.save(toUserDto(dto));
      return toUtilisateurDto(saved);
    } catch (InvalidEntityException ex) {
      // UserValidator (identity) porte exactement les memes regles/messages que l'ancien
      // UtilisateurValidator (legacy) : seul le code d'erreur est traduit, pas le message ni la
      // liste d'erreurs, pour rester byte-for-byte identique au contrat /utilisateurs/create.
      ErrorCodes legacyCode = ex.getErrorCode() == ErrorCodes.USER_ALREADY_EXISTS
          ? ErrorCodes.UTILISATEUR_ALREADY_EXISTS
          : ErrorCodes.UTILISATEUR_NOT_VALID;
      throw new InvalidEntityException(ex.getMessage(), legacyCode, ex.getErrors());
    }
  }

  @Override
  public UtilisateurDto findById(Long id) {
    if (id == null) {
      log.error("Utilisateur ID is null");
      return null;
    }
    return toUtilisateurDto(findUserOrThrow(() -> userService.findById(id),
        "Aucun utilisateur avec l'ID = " + id + " n' ete trouve dans la BDD"));
  }

  @Override
  public List<UtilisateurDto> findAll() {
    return userService.findAll().stream()
        .map(this::toUtilisateurDto)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Utilisateur ID is null");
      return;
    }
    userService.delete(id);
  }

  @Override
  public UtilisateurDto findByEmail(String email) {
    return toUtilisateurDto(findUserOrThrow(() -> userService.findByEmail(email),
        "Aucun utilisateur avec l'email = " + email + " n' ete trouve dans la BDD"));
  }

  @Override
  public UtilisateurDto changerMotDePasse(ChangerMotDePasseUtilisateurDto dto) {
    validate(dto);
    UserDto updated = findUserOrThrow(() -> userService.changePassword(dto.getId(), dto.getMotDePasse()),
        "Aucun utilisateur n'a ete trouve avec l'ID " + dto.getId());
    return toUtilisateurDto(updated);
  }

  private UserDto findUserOrThrow(Supplier<UserDto> lookup, String legacyMessage) {
    try {
      return lookup.get();
    } catch (EntityNotFoundException ex) {
      throw new EntityNotFoundException(legacyMessage, ErrorCodes.UTILISATEUR_NOT_FOUND);
    }
  }

  private void validate(ChangerMotDePasseUtilisateurDto dto) {
    if (dto == null) {
      log.warn("Impossible de modifier le mot de passe avec un objet NULL");
      throw new InvalidOperationException("Aucune information n'a ete fourni pour pouvoir changer le mot de passe",
          ErrorCodes.UTILISATEUR_CHANGE_PASSWORD_OBJECT_NOT_VALID);
    }
    if (dto.getId() == null) {
      log.warn("Impossible de modifier le mot de passe avec un ID NULL");
      throw new InvalidOperationException("ID utilisateur null:: Impossible de modifier le mote de passe",
          ErrorCodes.UTILISATEUR_CHANGE_PASSWORD_OBJECT_NOT_VALID);
    }
    if (!StringUtils.hasLength(dto.getMotDePasse()) || !StringUtils.hasLength(dto.getConfirmMotDePasse())) {
      log.warn("Impossible de modifier le mot de passe avec un mot de passe NULL");
      throw new InvalidOperationException("Mot de passe utilisateur null:: Impossible de modifier le mote de passe",
          ErrorCodes.UTILISATEUR_CHANGE_PASSWORD_OBJECT_NOT_VALID);
    }
    if (!dto.getMotDePasse().equals(dto.getConfirmMotDePasse())) {
      log.warn("Impossible de modifier le mot de passe avec deux mots de passe different");
      throw new InvalidOperationException("Mots de passe utilisateur non conformes:: Impossible de modifier le mote de passe",
          ErrorCodes.UTILISATEUR_CHANGE_PASSWORD_OBJECT_NOT_VALID);
    }
  }

  private UserDto toUserDto(UtilisateurDto dto) {
    Long idEntreprise = dto.getEntreprise() != null ? dto.getEntreprise().getId() : null;
    return UserDto.builder()
        .id(dto.getId())
        .nom(dto.getNom())
        .prenom(dto.getPrenom())
        .email(dto.getEmail())
        .dateDeNaissance(dto.getDateDeNaissance())
        .motDePasse(dto.getMoteDePasse())
        .adresse(dto.getAdresse())
        .photo(dto.getPhoto())
        .idEntreprise(idEntreprise)
        .build();
  }

  private UtilisateurDto toUtilisateurDto(UserDto user) {
    if (user == null) {
      return null;
    }
    TenantDto entreprise = null;
    if (user.getIdEntreprise() != null) {
      try {
        entreprise = tenantService.findById(user.getIdEntreprise());
      } catch (EntityNotFoundException ex) {
        entreprise = null;
      }
    }
    return UtilisateurDto.builder()
        .id(user.getId())
        .nom(user.getNom())
        .prenom(user.getPrenom())
        .email(user.getEmail())
        .dateDeNaissance(user.getDateDeNaissance())
        .moteDePasse(user.getMotDePasse())
        .adresse(user.getAdresse())
        .photo(user.getPhoto())
        .entreprise(entreprise)
        .build();
  }
}
