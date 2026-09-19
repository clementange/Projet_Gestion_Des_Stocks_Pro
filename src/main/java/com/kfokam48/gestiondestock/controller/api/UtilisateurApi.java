package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.UTILISATEUR_ENDPOINT;

import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "utilisateurs")
public interface UtilisateurApi {

  @PostMapping(UTILISATEUR_ENDPOINT + "/create")
  UtilisateurDto save(@RequestBody UtilisateurDto dto);

  // Phase 5a : IDOR corrige (self-only) - voir UtilisateurController.changerMotDePasse et
  // docs/phase-5a-report.md.
  @PostMapping(UTILISATEUR_ENDPOINT + "/update/password")
  UtilisateurDto changerMotDePasse(@RequestBody ChangerMotDePasseUtilisateurDto dto, @AuthenticationPrincipal ExtendedUser principal);

  @GetMapping(UTILISATEUR_ENDPOINT + "/{idUtilisateur}")
  UtilisateurDto findById(@PathVariable("idUtilisateur") Long id);

  @GetMapping(UTILISATEUR_ENDPOINT + "/find/{email}")
  UtilisateurDto findByEmail(@PathVariable("email") String email);

  @GetMapping(UTILISATEUR_ENDPOINT + "/all")
  List<UtilisateurDto> findAll();

  @DeleteMapping(UTILISATEUR_ENDPOINT + "/delete/{idUtilisateur}")
  void delete(@PathVariable("idUtilisateur") Long id);

}
