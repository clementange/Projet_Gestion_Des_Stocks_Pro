package com.kfokam48.gestiondestock.organization.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.organization.domain.model.City;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.validator.CityValidator;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.CityRepository;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.SiteRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CityServiceImpl implements CityService {

  private CityRepository cityRepository;
  private AuthorizationService authorizationService;
  private SiteRepository siteRepository;

  @Autowired
  public CityServiceImpl(CityRepository cityRepository, SiteRepository siteRepository,
      AuthorizationService authorizationService) {
    this.cityRepository = cityRepository;
    this.siteRepository = siteRepository;
    this.authorizationService = authorizationService;
  }

  @Override
  public CityDto save(CityDto dto, Long userId, Long organizationId) {
    requirePermission(userId, "CITY_CREATE", organizationId);
    // Phase 5b-2g : un id fourni par le client ferait de ce create un MERGE JPA, donc
    // l'ecrasement d'une ressource existante - voir docs/phase-5b2g-report.md.
    CreationIdGuard.requireNoId(dto.getId(), "d'une ville");
    // Phase 5e : l'organisation de la ressource creee est imposee ICI, depuis le parametre, et
    // non plus seulement par le controleur. Un service qui recoit organizationId sans l'appliquer
    // laisse tout appelant interne creer une ressource ORPHELINE (organisation nulle), invisible
    // aux lectures scopees - voir docs/phase-5e-report.md.
    dto.setOrganization(OrganizationDto.builder().id(organizationId).build());

    List<String> errors = CityValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("City is not valid {}", dto);
      throw new InvalidEntityException("La ville n'est pas valide", ErrorCodes.CITY_NOT_VALID, errors);
    }
    return CityDto.fromEntity(
        cityRepository.save(CityDto.toEntity(dto))
    );
  }

  @Override
  public CityDto findById(Long id) {
    if (id == null) {
      log.error("City ID is null");
      return null;
    }
    return cityRepository.findById(id)
        .map(CityDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune ville avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.CITY_NOT_FOUND)
        );
  }

  @Override
  public List<CityDto> findAllByOrganization(Long organizationId) {
    if (organizationId == null) {
      log.error("Organization ID is null");
      return List.of();
    }
    return cityRepository.findAllByOrganizationId(organizationId).stream()
        .map(CityDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("City ID is null");
      return;
    }
    List<Site> sites = siteRepository.findAllByCityId(id);
    if (!sites.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer cette ville qui possede des sites",
          ErrorCodes.CITY_ALREADY_IN_USE);
    }
    cityRepository.deleteById(id);
  }

  // Phase 5b-2g : voir docs/phase-5b2g-report.md.
  @Override
  public CityDto update(Long id, CityDto dto, Long userId, Long organizationId) {
    City existing = cityRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune ville avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CITY_NOT_FOUND));
    if (organizationId == null || existing.getOrganization() == null
        || !organizationId.equals(existing.getOrganization().getId())) {
      log.warn("City {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucune ville avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CITY_NOT_FOUND);
    }
    requirePermission(userId, "CITY_UPDATE", organizationId);
    dto.setId(id);
    dto.setOrganization(OrganizationDto.fromEntity(existing.getOrganization()));
    List<String> errors = CityValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("City is not valid {}", dto);
      throw new InvalidEntityException("La ville n'est pas valide", ErrorCodes.CITY_NOT_VALID, errors);
    }
    existing.setName(dto.getName());
    existing.setCountry(dto.getCountry());
    return CityDto.fromEntity(cityRepository.save(existing));
  }
  // Phase 5b-2h : voir docs/phase-5b2h-report.md.
  @Override
  public void delete(Long id, Long userId, Long organizationId) {
    if (id == null) {
      log.error("City ID is null");
      return;
    }
    City existing = cityRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune ville avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CITY_NOT_FOUND));
    Long ownerOrg = existing.getOrganization() == null ? null : existing.getOrganization().getId();
    if (organizationId == null || ownerOrg == null || !organizationId.equals(ownerOrg)) {
      log.warn("City {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucune ville avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CITY_NOT_FOUND);
    }
    // Phase 5c : appartenance (ci-dessus) -> permission -> ecriture.
    requirePermission(userId, "CITY_DELETE", organizationId);
    delete(id);
  }
  // Phase 5b-2h : voir docs/phase-5b2h-report.md.
  @Override
  public CityDto findById(Long id, Long organizationId) {
    City existing = cityRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune ville avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CITY_NOT_FOUND));
    if (organizationId == null || existing.getOrganization() == null
        || !organizationId.equals(existing.getOrganization().getId())) {
      log.warn("City {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucune ville avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CITY_NOT_FOUND);
    }
    return CityDto.fromEntity(existing);
  }
  /**
   * Phase 5c : ordre strict appartenance -> permission -> ecriture. L'appartenance est verifiee
   * en amont (5b-2g/2h) et renvoie un *_NOT_FOUND masque ; ici on repond a la question distincte
   * « qui, DANS l'organisation, a le droit de faire cela » - voir docs/rbac-matrix.md.
   */
  private void requirePermission(Long userId, String permissionCode, Long organizationId) {
    if (!authorizationService.hasPermission(userId, permissionCode, ScopeType.ORGANIZATION,
        organizationId, organizationId)) {
      log.warn("User {} lacks {} in organization {}", userId, permissionCode, organizationId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action",
          ErrorCodes.CITY_ACCESS_DENIED);
    }
  }
}
