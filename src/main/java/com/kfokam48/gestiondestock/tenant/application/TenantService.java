package com.kfokam48.gestiondestock.tenant.application;

import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import java.util.List;

/**
 * CRUD simple sur {@link com.kfokam48.gestiondestock.tenant.domain.model.Tenant}. {@code save()}
 * est un upsert plat, sans orchestration (Organization/Site/User/RBAC) - pour ca, voir
 * {@link TenantRegistrationService}. Cette separation evite le bug latent de
 * l'ancien EntrepriseServiceImpl.save() qui reexecutait toute l'orchestration a chaque mise a
 * jour (ex. changement de photo) - voir docs/phase-4b-report.md.
 */
public interface TenantService {

  TenantDto save(TenantDto dto);

  TenantDto findById(Long id);

  // Phase 5b-2c : self-only - id doit correspondre au tenant de l'appelant, meme 404 qu'un id
  // inexistant en cas de mismatch. findById(Long) sans verification reste utilise en interne
  // (ApplicationUserDetailsService au login pour resoudre organizationId, et l'adaptateur legacy
  // UtilisateurServiceImpl) - voir docs/phase-5b2c-report.md.
  TenantDto findById(Long id, Long callerTenantId);

  List<TenantDto> findAll(Long callerTenantId);

  void delete(Long id, Long callerTenantId);

  TenantDto updatePhoto(Long id, String url, Long callerTenantId);

}
