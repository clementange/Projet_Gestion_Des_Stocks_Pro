package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface SiteService {

  // Phase 5b-2d : verifie que la City referencee appartient bien a l'organisation de l'appelant,
  // recuperee fraiche depuis la base (jamais depuis les donnees imbriquees fournies par le
  // client) - meme principe que requireSiteInOrganization en 5b-2b - voir docs/phase-5b2d-report.md.
  SiteDto save(SiteDto dto, Long organizationId);

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
