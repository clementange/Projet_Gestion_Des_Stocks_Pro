package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import java.util.List;

public interface CustomerService {

  CustomerDto save(CustomerDto dto);

  /**
   * Phase 5b-2g : l'unique chemin de MISE A JOUR. L'id vient du CHEMIN, jamais du corps ;
   * l'entite est rechargee depuis la base et son appartenance a l'organisation de l'appelant
   * verifiee AVANT toute ecriture (404 fail-closed sinon) ; l'organisation elle-meme n'est
   * jamais modifiable - voir docs/phase-5b2g-report.md.
   */
  CustomerDto update(Long id, CustomerDto dto, Long organizationId);

  CustomerDto findById(Long id);

  // Phase 5b-2a : variante utilisee par le point d'entree HTTP en lecture, qui verifie que le
  // client appartient bien a l'organisation de l'appelant (meme 404 qu'un id inexistant en cas
  // de mismatch) - voir docs/phase-5b2a-report.md. findById(Long) sans organizationId reste
  // utilise en interne (updatePhoto).
  CustomerDto findById(Long id, Long organizationId);

  List<CustomerDto> findAll(Long organizationId);

  void delete(Long id);

  /**
   * Phase 5b-2h : seul chemin de suppression expose en HTTP. Verifie l'appartenance a
   * l'organisation de l'appelant AVANT de supprimer (404 fail-closed sinon) - voir
   * docs/phase-5b2h-report.md.
   */
  void delete(Long id, Long organizationId);

  CustomerDto updatePhoto(Long id, String url, Long organizationId);

}
