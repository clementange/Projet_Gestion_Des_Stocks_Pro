package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.ChangerMotDePasseUtilisateurDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import java.util.List;

public interface UtilisateurService {

  UtilisateurDto save(UtilisateurDto dto);

  UtilisateurDto findById(Long id, Long callerTenantId);

  List<UtilisateurDto> findAll(Long callerTenantId);

  void delete(Long id, Long callerTenantId);

  UtilisateurDto findByEmail(String email, Long callerTenantId);

  UtilisateurDto changerMotDePasse(ChangerMotDePasseUtilisateurDto dto);


}
