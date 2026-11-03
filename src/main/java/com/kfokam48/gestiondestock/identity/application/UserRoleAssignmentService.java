package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import java.util.List;

public interface UserRoleAssignmentService {

  UserRoleAssignmentDto save(UserRoleAssignmentDto dto);

  /**
   * Phase 5f : variante utilisee par le point d'entree HTTP. Elle exige que l'utilisateur cible
   * appartienne au tenant de l'appelant ({@code callerTenantId}, soit {@code User.idEntreprise}).
   *
   * <p>La variante sans controle reste necessaire a l'AMORCAGE d'un tenant
   * ({@code TenantRegistrationServiceImpl} accorde ADMINISTRATEUR a l'admin qu'il vient de creer,
   * sans principal donc sans tenant appelant) - voir docs/phase-5f-report.md.
   */
  UserRoleAssignmentDto save(UserRoleAssignmentDto dto, Long callerTenantId);

  // Phase 5b-2c : organizationId verifie contre celui de l'affectation, meme 404 qu'un id
  // inexistant en cas de mismatch - independant de toute logique RBAC (meme classe de garde-fou
  // que requireSiteInOrganization en 5b-2b). Pas d'appelant interne pour findById - voir
  // docs/phase-5b2c-report.md.
  UserRoleAssignmentDto findById(Long id, Long organizationId);

  // findAllByUser(Long) sans organizationId reste utilise en interne
  // (ApplicationUserDetailsService au login, pour resoudre les authorities) - voir
  // docs/phase-5b2c-report.md.
  List<UserRoleAssignmentDto> findAllByUser(Long userId);

  List<UserRoleAssignmentDto> findAllByUser(Long userId, Long organizationId);

  void delete(Long id);

}
