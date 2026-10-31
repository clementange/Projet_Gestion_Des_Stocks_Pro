package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import java.util.List;

public interface CityService {

  CityDto save(CityDto dto);

  /**
   * Phase 5b-2g : l'unique chemin de MISE A JOUR. L'id vient du CHEMIN, jamais du corps ;
   * l'entite est rechargee depuis la base et son appartenance a l'organisation de l'appelant
   * verifiee AVANT toute ecriture (404 fail-closed sinon) ; l'organisation elle-meme n'est
   * jamais modifiable - voir docs/phase-5b2g-report.md.
   */
  CityDto update(Long id, CityDto dto, Long organizationId);

  CityDto findById(Long id);

  List<CityDto> findAllByOrganization(Long organizationId);

  void delete(Long id);

}
