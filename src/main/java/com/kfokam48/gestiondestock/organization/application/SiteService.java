package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface SiteService {

  SiteDto save(SiteDto dto);

  SiteDto findById(Long id);

  // Phase 5b-2b : variante utilisee par le point d'entree HTTP en lecture, qui verifie que le
  // site appartient bien a l'organisation de l'appelant (meme 404 qu'un id inexistant en cas de
  // mismatch) - voir docs/phase-5b2b-report.md. findById(Long) sans organizationId reste utilise
  // en interne (ReportingServiceImpl.getSalesSummary, et desormais aussi les verifications
  // d'appartenance de site a la creation de Sale/CustomerOrder/PurchaseOrder/StockTransfer).
  SiteDto findById(Long id, Long organizationId);

  SiteDto findByCode(String code, Long organizationId);

  List<SiteDto> findAllByCity(Long cityId, Long organizationId);

  List<SiteDto> findAll(Long organizationId);

  void delete(Long id);

}
