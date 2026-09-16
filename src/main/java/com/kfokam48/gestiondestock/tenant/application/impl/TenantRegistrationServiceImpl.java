package com.kfokam48.gestiondestock.tenant.application.impl;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.tenant.application.TenantRegistrationService;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import com.kfokam48.gestiondestock.tenant.application.validator.TenantRegistrationValidator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migre depuis EntrepriseServiceImpl.save() (Phase 4b) : meme orchestration (miroir Organization
 * + Site par defaut, amorcage RBAC ADMINISTRATEUR/GLOBAL), mais le mot de passe admin vient
 * desormais de la requete au lieu d'etre genere par le serveur - voir docs/phase-4b-report.md.
 */
@Transactional(rollbackFor = Exception.class)
@Service
@Slf4j
public class TenantRegistrationServiceImpl implements TenantRegistrationService {

  private TenantService tenantService;
  private OrganizationService organizationService;
  private UserService userService;
  private RoleService roleService;
  private UserRoleAssignmentService userRoleAssignmentService;

  @Autowired
  public TenantRegistrationServiceImpl(TenantService tenantService, OrganizationService organizationService,
      UserService userService, RoleService roleService, UserRoleAssignmentService userRoleAssignmentService) {
    this.tenantService = tenantService;
    this.organizationService = organizationService;
    this.userService = userService;
    this.roleService = roleService;
    this.userRoleAssignmentService = userRoleAssignmentService;
  }

  @Override
  public TenantDto register(TenantRegistrationRequest request) {
    List<String> errors = TenantRegistrationValidator.validate(request);
    if (!errors.isEmpty()) {
      log.error("Tenant registration is not valid {}", request);
      throw new InvalidEntityException("L'inscription n'est pas valide", ErrorCodes.TENANT_REGISTRATION_NOT_VALID, errors);
    }

    TenantDto tenant = tenantService.save(TenantDto.builder()
        .nom(request.nom())
        .description(request.description())
        .adresse(request.adresse())
        .codeFiscal(request.codeFiscal())
        .photo(request.photo())
        .email(request.email())
        .numTel(request.numTel())
        .steWeb(request.steWeb())
        .build());

    // Miroir Organization (Phase 16, reconduit ici tel quel) : les modules neufs
    // (organization/purchasing/sales/inventory/transfers) s'appuient sur organizationId, pas sur
    // idEntreprise directement.
    OrganizationDto organization = organizationService.save(OrganizationDto.builder()
        .name(tenant.getNom())
        .description(tenant.getDescription())
        .active(true)
        .email(tenant.getEmail())
        .phone(tenant.getNumTel())
        .website(tenant.getSteWeb())
        .taxCode(tenant.getCodeFiscal())
        .photo(tenant.getPhoto())
        .adresse(tenant.getAdresse())
        .build());
    organizationService.ensureDefaultSite(organization.getId());

    tenant.setOrganizationId(organization.getId());
    tenant = tenantService.save(tenant);

    // UserValidator exige une adresse (meme regle qu'UtilisateurValidator avant Phase 4a) ; la
    // requete d'inscription n'a pas de champ adresse admin distinct, on reutilise celle de
    // l'entreprise - meme comportement que l'ancien EntrepriseServiceImpl.fromEntreprise().
    UserDto admin = userService.save(UserDto.builder()
        .nom(request.adminNom())
        .prenom(request.adminPrenom())
        .email(request.adminEmail())
        .dateDeNaissance(request.adminDateDeNaissance())
        .motDePasse(request.motDePasse())
        .adresse(request.adresse())
        .idEntreprise(tenant.getId())
        .build());

    // Amorcage RBAC (Phase 14, reconduit ici tel quel) : le premier utilisateur d'une
    // organisation recoit d'emblee le role ADMINISTRATEUR en perimetre GLOBAL, sinon personne ne
    // pourrait jamais attribuer de role via user-role-assignments/create (aucune permission codee
    // en dur : ADMINISTRATEUR et ses permissions sont des donnees seedees par
    // V3__seed_baseline_rbac_permissions.sql, pas une branche "if roleName.equals(ADMIN)" dans le
    // code applicatif).
    userRoleAssignmentService.save(UserRoleAssignmentDto.builder()
        .userId(admin.getId())
        .role(roleService.findByCode("ADMINISTRATEUR"))
        .scopeType(ScopeType.GLOBAL)
        .build());

    return tenant;
  }
}
