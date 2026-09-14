package com.kfokam48.gestiondestock.controller;


import com.kfokam48.gestiondestock.controller.api.UtilisateurApi;
import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.services.UtilisateurService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
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

  @Override
  public UtilisateurDto changerMotDePasse(ChangerMotDePasseUtilisateurDto dto) {
    return utilisateurService.changerMotDePasse(dto);
  }

  @Override
  public UtilisateurDto findById(Long id) {
    return utilisateurService.findById(id);
  }

  @Override
  public UtilisateurDto findByEmail(String email) {
    return utilisateurService.findByEmail(email);
  }

  @Override
  public List<UtilisateurDto> findAll() {
    return utilisateurService.findAll();
  }

  @Override
  public void delete(Long id) {
    utilisateurService.delete(id);
  }
}
