package com.kfokam48.gestiondestock.organization.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.application.validator.SiteValidator;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.SiteRepository;
import com.kfokam48.gestiondestock.organization.infrastructure.persistence.WarehouseRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class SiteServiceImpl implements SiteService {

  private SiteRepository siteRepository;
  private WarehouseRepository warehouseRepository;
  private CityService cityService;

  @Autowired
  public SiteServiceImpl(SiteRepository siteRepository, WarehouseRepository warehouseRepository, CityService cityService) {
    this.siteRepository = siteRepository;
    this.warehouseRepository = warehouseRepository;
    this.cityService = cityService;
  }

  @Override
  public SiteDto save(SiteDto dto, Long organizationId) {
    List<String> errors = SiteValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Site is not valid {}", dto);
      throw new InvalidEntityException("Le site n'est pas valide", ErrorCodes.SITE_NOT_VALID, errors);
    }
    requireCityInOrganization(dto.getCity().getId(), organizationId);
    return SiteDto.fromEntity(
        siteRepository.save(SiteDto.toEntity(dto))
    );
  }

  // Phase 5b-2d : voir docs/phase-5b2d-report.md.
  private void requireCityInOrganization(Long cityId, Long organizationId) {
    CityDto city = cityService.findById(cityId);
    boolean matches = city != null && city.getOrganization() != null
        && organizationId != null && organizationId.equals(city.getOrganization().getId());
    if (!matches) {
      log.warn("City {} does not belong to organization {}", cityId, organizationId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission de creer un site dans cette ville",
          ErrorCodes.SITE_ACCESS_DENIED);
    }
  }

  @Override
  public SiteDto findById(Long id) {
    if (id == null) {
      log.error("Site ID is null");
      return null;
    }
    return siteRepository.findById(id)
        .map(SiteDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun site avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.SITE_NOT_FOUND)
        );
  }

  @Override
  public SiteDto findById(Long id, Long organizationId) {
    if (id == null) {
      log.error("Site ID is null");
      return null;
    }
    return siteRepository.findById(id)
        .filter(site -> belongsToOrganization(site, organizationId))
        .map(SiteDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun site avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.SITE_NOT_FOUND)
        );
  }

  @Override
  public SiteDto findByCode(String code, Long organizationId) {
    if (!StringUtils.hasLength(code)) {
      log.error("Site CODE is null");
      return null;
    }
    return siteRepository.findSiteByCode(code)
        .filter(site -> belongsToOrganization(site, organizationId))
        .map(SiteDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun site avec le CODE = " + code + " n'a ete trouve dans la BDD",
            ErrorCodes.SITE_NOT_FOUND)
        );
  }

  @Override
  public List<SiteDto> findAllByCity(Long cityId, Long organizationId) {
    if (cityId == null) {
      log.error("City ID is null");
      return List.of();
    }
    return siteRepository.findAllByCityId(cityId).stream()
        .filter(site -> belongsToOrganization(site, organizationId))
        .map(SiteDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<SiteDto> findAll(Long organizationId) {
    return siteRepository.findAll().stream()
        .filter(site -> belongsToOrganization(site, organizationId))
        .map(SiteDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2b : les deux cotes doivent etre non-null pour matcher - voir docs/phase-5b2b-report.md.
  private boolean belongsToOrganization(Site site, Long organizationId) {
    return organizationId != null
        && site.getCity() != null
        && site.getCity().getOrganization() != null
        && organizationId.equals(site.getCity().getOrganization().getId());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Site ID is null");
      return;
    }
    if (warehouseRepository.findWarehouseBySiteId(id).isPresent()) {
      throw new InvalidOperationException("Impossible de supprimer ce site qui est rattache a un entrepot",
          ErrorCodes.SITE_ALREADY_IN_USE);
    }
    siteRepository.deleteById(id);
  }
}
