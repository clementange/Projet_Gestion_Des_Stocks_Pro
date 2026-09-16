package com.kfokam48.gestiondestock.tenant.application;

import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;

/**
 * Cas d'usage d'inscription self-service : cree le Tenant, son Organization miroir + Site par
 * defaut, son premier utilisateur administrateur (avec le mot de passe fourni par l'appelant -
 * plus de generation serveur, voir docs/phase-4b-report.md) et amorce le RBAC
 * (role ADMINISTRATEUR, perimetre GLOBAL). Orchestration multi-agregats (Tenant, Organization,
 * Site, User, UserRoleAssignment) : deliberement separee du CRUD simple de {@link TenantService},
 * pas un {@code save()} fourre-tout.
 */
public interface TenantRegistrationService {

  TenantDto register(TenantRegistrationRequest request);

}
