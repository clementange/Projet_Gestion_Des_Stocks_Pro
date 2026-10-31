package com.kfokam48.gestiondestock.controller;


import com.kfokam48.gestiondestock.controller.api.UtilisateurApi;
import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.services.UtilisateurService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class UtilisateurController implements UtilisateurApi {

  private UtilisateurService utilisateurService;

  @Autowired
  public UtilisateurController(UtilisateurService utilisateurService) {
    this.utilisateurService = utilisateurService;
  }

  @Override
  public UtilisateurDto save(UtilisateurDto dto) {
    return utilisateurService.save(dto);
  }

  // Phase 5a : IDOR corrige (self-only, meme choix que identity.UserController.changePassword) -
  // voir docs/phase-5a-report.md. Correctif de securite sur une fonctionnalite existante, pas une
  // fonctionnalite neuve : reste dans l'adaptateur legacy plutot que d'exiger un deplacement.
  @Override
  public UtilisateurDto changerMotDePasse(ChangerMotDePasseUtilisateurDto dto, ExtendedUser principal) {
    Long callerId = principal == null ? null : principal.getIdUtilisateur();
    if (callerId == null || !callerId.equals(dto.getId())) {
      log.warn("User {} tried to change the password of user {}", callerId, dto.getId());
      throw new InvalidOperationException(
          "Vous ne pouvez changer que votre propre mot de passe",
          ErrorCodes.USER_CHANGE_PASSWORD_FORBIDDEN);
    }
    return utilisateurService.changerMotDePasse(dto);
  }

  @Override
  public UtilisateurDto findById(Long id, ExtendedUser principal) {
    return utilisateurService.findById(id, principal.getIdEntreprise());
  }

  @Override
  public UtilisateurDto findByEmail(String email, ExtendedUser principal) {
    return utilisateurService.findByEmail(email, principal.getIdEntreprise());
  }

  @Override
  public List<UtilisateurDto> findAll(ExtendedUser principal) {
    return utilisateurService.findAll(principal.getIdEntreprise());
  }

  @Override
  public void delete(Long id, ExtendedUser principal) {
    utilisateurService.delete(id, principal.getIdEntreprise());
  }
}
