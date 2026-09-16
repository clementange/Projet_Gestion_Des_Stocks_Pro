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

  List<TenantDto> findAll();

  void delete(Long id);

}
