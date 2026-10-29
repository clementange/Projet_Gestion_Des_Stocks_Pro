package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface OrganizationService {

  /**
   * Phase 5b-2e : plus aucune surface HTTP ne mene ici ({@code POST /organizations/create}
   * supprime). Seul appelant restant : {@code TenantRegistrationServiceImpl}, qui cree
   * l'Organization miroir d'un Tenant dans le cadre de l'orchestration complete d'inscription
   * (Tenant + Organization + Site par defaut + admin + RBAC). Ne pas reexposer en HTTP sans
   * cette orchestration : une Organization seule est une orpheline inatteignable - voir
   * docs/phase-5b2e-report.md.
   */
  OrganizationDto save(OrganizationDto dto);

  // Phase 5b-2c : self-only - id doit correspondre a l'organisation de l'appelant, meme 404
  // qu'un id inexistant en cas de mismatch - voir docs/phase-5b2c-report.md.
  OrganizationDto findById(Long id, Long callerOrganizationId);

  List<OrganizationDto> findAll(Long callerOrganizationId);

  void delete(Long id, Long callerOrganizationId);

  // Site implicite pour les endpoints legacy sans notion de site ; cree au premier appel, idempotent ensuite.
  SiteDto ensureDefaultSite(Long organizationId);

}
