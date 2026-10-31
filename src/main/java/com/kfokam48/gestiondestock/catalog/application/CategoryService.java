package com.kfokam48.gestiondestock.catalog.application;

import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import java.util.List;

public interface CategoryService {

  CategoryDto save(CategoryDto dto);

  /**
   * Phase 5b-2g : l'unique chemin de MISE A JOUR. L'id vient du CHEMIN, jamais du corps ;
   * l'entite est rechargee depuis la base et son appartenance a l'organisation de l'appelant
   * verifiee AVANT toute ecriture (404 fail-closed sinon) ; l'organisation elle-meme n'est
   * jamais modifiable - voir docs/phase-5b2g-report.md.
   */
  CategoryDto update(Long id, CategoryDto dto, Long organizationId);

  // Phase 5b-2a : organizationId verifie contre celui de la categorie (meme 404 qu'un id
  // inexistant en cas de mismatch) - voir docs/phase-5b2a-report.md.
  CategoryDto findById(Long id, Long organizationId);

  CategoryDto findByCode(String code, Long organizationId);

  List<CategoryDto> findAll(Long organizationId);

  void delete(Long id);

}
