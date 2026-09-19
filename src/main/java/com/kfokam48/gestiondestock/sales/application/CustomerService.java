package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import java.util.List;

public interface CustomerService {

  CustomerDto save(CustomerDto dto);

  CustomerDto findById(Long id);

  // Phase 5b-2a : variante utilisee par le point d'entree HTTP en lecture, qui verifie que le
  // client appartient bien a l'organisation de l'appelant (meme 404 qu'un id inexistant en cas
  // de mismatch) - voir docs/phase-5b2a-report.md. findById(Long) sans organizationId reste
  // utilise en interne (updatePhoto).
  CustomerDto findById(Long id, Long organizationId);

  List<CustomerDto> findAll(Long organizationId);

  void delete(Long id);

  CustomerDto updatePhoto(Long id, String url);

}
