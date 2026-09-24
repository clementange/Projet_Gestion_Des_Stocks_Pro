package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import java.util.List;

public interface UserService {

  UserDto save(UserDto dto);

  UserDto findById(Long id);

  // Phase 5b-2c : scope via User.idEntreprise (Tenant.id), pas organizationId - meme 404 qu'un id
  // inexistant en cas de mismatch. findById(Long)/findByEmail(String)/findAll()/delete(Long) sans
  // verification restent utilises en interne (ApplicationUserDetailsService au login - findByEmail
  // doit rester global, resolu avant tout contexte tenant - et l'adaptateur legacy
  // UtilisateurServiceImpl) - voir docs/phase-5b2c-report.md.
  UserDto findById(Long id, Long callerTenantId);

  UserDto findByEmail(String email);

  UserDto findByEmail(String email, Long callerTenantId);

  List<UserDto> findAll();

  List<UserDto> findAll(Long callerTenantId);

  void delete(Long id);

  void delete(Long id, Long callerTenantId);

  UserDto changePassword(Long id, String rawPassword);

  UserDto updatePhoto(Long id, String url);

}
