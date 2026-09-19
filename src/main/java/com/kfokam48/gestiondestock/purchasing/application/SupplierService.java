package com.kfokam48.gestiondestock.purchasing.application;

import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import java.util.List;

public interface SupplierService {

  SupplierDto save(SupplierDto dto);

  SupplierDto findById(Long id);

  // Phase 5b-2a : variante utilisee par le point d'entree HTTP en lecture, qui verifie que le
  // fournisseur appartient bien a l'organisation de l'appelant (meme 404 qu'un id inexistant en
  // cas de mismatch) - voir docs/phase-5b2a-report.md. findById(Long) sans organizationId reste
  // utilise en interne (updatePhoto).
  SupplierDto findById(Long id, Long organizationId);

  List<SupplierDto> findAll(Long organizationId);

  void delete(Long id);

  SupplierDto updatePhoto(Long id, String url);

}
