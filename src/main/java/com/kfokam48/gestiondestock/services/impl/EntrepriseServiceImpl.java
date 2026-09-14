package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.dto.EntrepriseDto;
import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.services.EntrepriseService;
import com.kfokam48.gestiondestock.services.UtilisateurService;
import com.kfokam48.gestiondestock.validator.EntrepriseValidator;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
@Service
@Slf4j
public class EntrepriseServiceImpl implements EntrepriseService {

  private EntrepriseRepository entrepriseRepository;
  private UtilisateurService utilisateurService;
  private RoleService roleService;
  private UserRoleAssignmentService userRoleAssignmentService;
  private OrganizationService organizationService;

  @Autowired
  public EntrepriseServiceImpl(EntrepriseRepository entrepriseRepository, UtilisateurService utilisateurService,
      RoleService roleService, UserRoleAssignmentService userRoleAssignmentService,
      OrganizationService organizationService) {
    this.entrepriseRepository = entrepriseRepository;
    this.utilisateurService = utilisateurService;
    this.roleService = roleService;
    this.userRoleAssignmentService = userRoleAssignmentService;
    this.organizationService = organizationService;
  }

  @Override
  public EntrepriseDto save(EntrepriseDto dto) {
    List<String> errors = EntrepriseValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Entreprise is not valid {}", dto);
      throw new InvalidEntityException("L'entreprise n'est pas valide", ErrorCodes.ENTREPRISE_NOT_VALID, errors);
    }
    Entreprise savedEntrepriseEntity = entrepriseRepository.save(EntrepriseDto.toEntity(dto));

    // Miroir Organization (Phase 16) : les endpoints legacy restent inchanges, mais les phases
    // 18-22 resolvent idEntreprise -> organizationId pour s'appuyer sur les modules neufs.
    OrganizationDto organizationDto = OrganizationDto.builder()
        .name(savedEntrepriseEntity.getNom())
        .description(savedEntrepriseEntity.getDescription())
        .active(true)
        .email(savedEntrepriseEntity.getEmail())
        .phone(savedEntrepriseEntity.getNumTel())
        .website(savedEntrepriseEntity.getSteWeb())
        .taxCode(savedEntrepriseEntity.getCodeFiscal())
        .photo(savedEntrepriseEntity.getPhoto())
        .adresse(AdresseDto.fromEntity(savedEntrepriseEntity.getAdresse()))
        .build();
    OrganizationDto savedOrganization = organizationService.save(organizationDto);
    organizationService.ensureDefaultSite(savedOrganization.getId());

    savedEntrepriseEntity.setOrganizationId(savedOrganization.getId());
    savedEntrepriseEntity = entrepriseRepository.save(savedEntrepriseEntity);

    EntrepriseDto savedEntreprise = EntrepriseDto.fromEntity(savedEntrepriseEntity);

    UtilisateurDto utilisateur = fromEntreprise(savedEntreprise);

    UtilisateurDto savedUser = utilisateurService.save(utilisateur);

    // Amorcage RBAC (Phase 14) : le premier utilisateur d'une organisation recoit d'emblee le
    // role ADMINISTRATEUR en perimetre GLOBAL, sinon personne ne pourrait jamais attribuer de
    // role via user-role-assignments/create (aucune permission codee en dur : ADMINISTRATEUR
    // et ses permissions sont des donnees seedees par V3__seed_baseline_rbac_permissions.sql,
    // pas une branche "if roleName.equals(ADMIN)" dans le code applicatif).
    UserRoleAssignmentDto assignment = UserRoleAssignmentDto.builder()
        .userId(savedUser.getId())
        .role(roleService.findByCode("ADMINISTRATEUR"))
        .scopeType(ScopeType.GLOBAL)
        .build();
    userRoleAssignmentService.save(assignment);

    return  savedEntreprise;
  }

  private UtilisateurDto fromEntreprise(EntrepriseDto dto) {
    return UtilisateurDto.builder()
        .adresse(dto.getAdresse())
        .nom(dto.getNom())
        .prenom(dto.getCodeFiscal())
        .email(dto.getEmail())
        .moteDePasse(generateRandomPassword())
        .entreprise(dto)
        .dateDeNaissance(Instant.now())
        .photo(dto.getPhoto())
        .build();
  }

  private String generateRandomPassword() {
    return "som3R@nd0mP@$$word";
  }

  @Override
  public EntrepriseDto findById(Long id) {
    if (id == null) {
      log.error("Entreprise ID is null");
      return null;
    }
    return entrepriseRepository.findById(id)
        .map(EntrepriseDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune entreprise avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.ENTREPRISE_NOT_FOUND)
        );
  }

  @Override
  public List<EntrepriseDto> findAll() {
    return entrepriseRepository.findAll().stream()
        .map(EntrepriseDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Entreprise ID is null");
      return;
    }
    entrepriseRepository.deleteById(id);
  }
}
