package com.kfokam48.gestiondestock.organization.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
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
  private SiteRepository siteRepository;

  @Autowired
  public CityServiceImpl(CityRepository cityRepository, SiteRepository siteRepository) {
    this.cityRepository = cityRepository;
    this.siteRepository = siteRepository;
  }

  @Override
  public CityDto save(CityDto dto) {
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
}
