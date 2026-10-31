package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import java.util.List;

public interface WarehouseService {

  // Phase 5b-2g : le Site reference doit appartenir a l'organisation de l'appelant - le controle
  // de type ENTREPOT existant ne regardait que le type, jamais le proprietaire du site - voir
  // docs/phase-5b2g-report.md.
  WarehouseDto save(WarehouseDto dto, Long organizationId);

  /**
   * Phase 5b-2g : l'unique chemin de MISE A JOUR. L'id vient du CHEMIN, jamais du corps ;
   * l'entite est rechargee depuis la base et son appartenance a l'organisation de l'appelant
   * verifiee AVANT toute ecriture (404 fail-closed sinon) ; l'organisation elle-meme n'est
   * jamais modifiable - voir docs/phase-5b2g-report.md.
   */
  WarehouseDto update(Long id, WarehouseDto dto, Long organizationId);

  WarehouseDto findById(Long id);

  WarehouseDto findByCode(String code);

  List<WarehouseDto> findAll();

  void delete(Long id);

}
