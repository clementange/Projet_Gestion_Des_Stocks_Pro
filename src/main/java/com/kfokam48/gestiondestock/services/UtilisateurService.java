package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import java.util.List;

public interface UtilisateurService {

  UtilisateurDto save(UtilisateurDto dto);

  UtilisateurDto findById(Long id);

  List<UtilisateurDto> findAll();

  void delete(Long id);

  UtilisateurDto findByEmail(String email);

  UtilisateurDto changerMotDePasse(ChangerMotDePasseUtilisateurDto dto);


}
