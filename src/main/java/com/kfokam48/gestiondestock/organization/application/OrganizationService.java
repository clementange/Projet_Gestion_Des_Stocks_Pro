package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface OrganizationService {

  OrganizationDto save(OrganizationDto dto);

  // Phase 5b-2c : self-only - id doit correspondre a l'organisation de l'appelant, meme 404
  // qu'un id inexistant en cas de mismatch - voir docs/phase-5b2c-report.md.
  OrganizationDto findById(Long id, Long callerOrganizationId);

  List<OrganizationDto> findAll(Long callerOrganizationId);

  void delete(Long id, Long callerOrganizationId);

  // Site implicite pour les endpoints legacy sans notion de site ; cree au premier appel, idempotent ensuite.
  SiteDto ensureDefaultSite(Long organizationId);

}
