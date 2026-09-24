package com.kfokam48.gestiondestock.organization.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.application.validator.OrganizationValidator;
import com.kfokam48.gestiondestock.organization.domain.model.City;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.CityRepository;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.OrganizationRepository;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.SiteRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrganizationServiceImpl implements OrganizationService {

  private OrganizationRepository organizationRepository;
  private CityRepository cityRepository;
  private SiteRepository siteRepository;

  @Autowired
  public OrganizationServiceImpl(OrganizationRepository organizationRepository, CityRepository cityRepository,
      SiteRepository siteRepository) {
    this.organizationRepository = organizationRepository;
    this.cityRepository = cityRepository;
    this.siteRepository = siteRepository;
  }

  @Override
  public OrganizationDto save(OrganizationDto dto) {
    List<String> errors = OrganizationValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Organization is not valid {}", dto);
      throw new InvalidEntityException("L'organisation n'est pas valide", ErrorCodes.ORGANIZATION_NOT_VALID, errors);
    }
    return OrganizationDto.fromEntity(
        organizationRepository.save(OrganizationDto.toEntity(dto))
    );
  }

  @Override
  public OrganizationDto findById(Long id, Long callerOrganizationId) {
    if (id == null) {
      log.error("Organization ID is null");
      return null;
    }
    if (!id.equals(callerOrganizationId)) {
      throw new EntityNotFoundException(
          "Aucune organisation avec l'ID = " + id + " n'a ete trouvee dans la BDD",
          ErrorCodes.ORGANIZATION_NOT_FOUND);
    }
    return organizationRepository.findById(id)
        .map(OrganizationDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune organisation avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.ORGANIZATION_NOT_FOUND)
        );
  }

  // Phase 5b-2c : self-only - au plus une organisation (la sienne) - voir docs/phase-5b2c-report.md.
  @Override
  public List<OrganizationDto> findAll(Long callerOrganizationId) {
    return organizationRepository.findAll().stream()
        .filter(org -> callerOrganizationId != null && callerOrganizationId.equals(org.getId()))
        .map(OrganizationDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id, Long callerOrganizationId) {
    if (id == null) {
      log.error("Organization ID is null");
      return;
    }
    if (!id.equals(callerOrganizationId)) {
      throw new EntityNotFoundException(
          "Aucune organisation avec l'ID = " + id + " n'a ete trouvee dans la BDD",
          ErrorCodes.ORGANIZATION_NOT_FOUND);
    }
    List<City> cities = cityRepository.findAllByOrganizationId(id);
    if (!cities.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer cette organisation qui possede des villes",
          ErrorCodes.ORGANIZATION_ALREADY_IN_USE);
    }
    organizationRepository.deleteById(id);
  }

  @Override
  public SiteDto ensureDefaultSite(Long organizationId) {
    if (organizationId == null) {
      log.error("Organization ID is null");
      return null;
    }
    String defaultSiteCode = "ORG-" + organizationId + "-DEFAULT";
    return siteRepository.findSiteByCode(defaultSiteCode)
        .map(SiteDto::fromEntity)
        .orElseGet(() -> SiteDto.fromEntity(createDefaultSite(organizationId, defaultSiteCode)));
  }

  private Site createDefaultSite(Long organizationId, String code) {
    Organization organization = organizationRepository.findById(organizationId)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune organisation avec l'ID = " + organizationId + " n'a ete trouvee dans la BDD",
            ErrorCodes.ORGANIZATION_NOT_FOUND));

    City city = new City();
    city.setName("Siege");
    city.setOrganization(organization);
    City savedCity = cityRepository.save(city);

    Site site = new Site();
    site.setCode(code);
    site.setName("Site principal");
    site.setType(SiteType.ENTREPOT);
    site.setActive(true);
    site.setCity(savedCity);
    return siteRepository.save(site);
  }
}
