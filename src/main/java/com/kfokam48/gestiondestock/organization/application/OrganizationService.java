package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface OrganizationService {

  OrganizationDto save(OrganizationDto dto);

  OrganizationDto findById(Long id);

  List<OrganizationDto> findAll();

  void delete(Long id);

  // Site implicite pour les endpoints legacy sans notion de site ; cree au premier appel, idempotent ensuite.
  SiteDto ensureDefaultSite(Long organizationId);

}
