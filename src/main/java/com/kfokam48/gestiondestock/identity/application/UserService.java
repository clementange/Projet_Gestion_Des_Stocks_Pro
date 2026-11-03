package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import java.util.List;

public interface UserService {

  UserDto save(UserDto dto);

  /**
   * Phase 5c : variante gardee par USER_CREATE, utilisee par le point d'entree HTTP. La variante
   * sans permission reste necessaire a l'AMORCAGE d'un tenant : l'admin bootstrap est cree avant
   * qu'aucune affectation RBAC n'existe, donc aucune permission ne pourrait etre verifiee a ce
   * moment-la - voir docs/rbac-matrix.md.
   *
   * <p>Phase 5d : {@code callerTenantId} IMPOSE le tenant de l'utilisateur cree ; la valeur
   * {@code idEntreprise} du corps de la requete est ignoree. Sans cela, l'admin d'un tenant
   * pouvait implanter un compte — mot de passe compris — dans le tenant d'autrui. C'est pourquoi
   * cette surcharge prend le tenant de l'appelant et la variante d'amorcage, non : elle seule a
   * le droit de choisir le tenant - voir docs/phase-5d-report.md.
   */
  UserDto save(UserDto dto, Long callerTenantId, Long userId, Long organizationId);

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

  // Phase 5c : variante gardee par USER_DELETE. La variante a 2 arguments (appartenance seule)
  // reste utilisee par l'adaptateur legacy /utilisateurs/delete - gap de permission assume, cette
  // route devant disparaitre en 4e.
  void delete(Long id, Long callerTenantId, Long userId, Long organizationId);

  UserDto changePassword(Long id, String rawPassword);

  UserDto updatePhoto(Long id, String url);

}
