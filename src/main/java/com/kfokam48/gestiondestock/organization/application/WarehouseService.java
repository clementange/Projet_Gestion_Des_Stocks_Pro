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

  /**
   * Phase 5b-2h : lecture scopee a l'organisation de l'appelant (404 fail-closed). City et
   * Warehouse n'avaient JAMAIS ete couvertes par 5b-2a/b/c - voir docs/phase-5b2h-report.md.
   */
  WarehouseDto findById(Long id, Long organizationId);

  WarehouseDto findByCode(String code, Long organizationId);

  List<WarehouseDto> findAll(Long organizationId);

  void delete(Long id);

  /**
   * Phase 5b-2h : seul chemin de suppression expose en HTTP. Verifie l'appartenance a
   * l'organisation de l'appelant AVANT de supprimer (404 fail-closed sinon) - voir
   * docs/phase-5b2h-report.md.
   */
  void delete(Long id, Long organizationId);

}
