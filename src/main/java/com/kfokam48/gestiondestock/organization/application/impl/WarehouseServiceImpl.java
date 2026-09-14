package com.kfokam48.gestiondestock.organization.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.organization.application.WarehouseService;
import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import com.kfokam48.gestiondestock.organization.application.validator.WarehouseValidator;
import com.kfokam48.gestiondestock.organization.domain.model.Site;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
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
public class WarehouseServiceImpl implements WarehouseService {

  private WarehouseRepository warehouseRepository;
  private SiteRepository siteRepository;

  @Autowired
  public WarehouseServiceImpl(WarehouseRepository warehouseRepository, SiteRepository siteRepository) {
    this.warehouseRepository = warehouseRepository;
    this.siteRepository = siteRepository;
  }

  @Override
  public WarehouseDto save(WarehouseDto dto) {
    List<String> errors = WarehouseValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Warehouse is not valid {}", dto);
      throw new InvalidEntityException("L'entrepot n'est pas valide", ErrorCodes.WAREHOUSE_NOT_VALID, errors);
    }
    // Invariant metier verifiee contre le Site reellement persiste, pas contre ce que le client
    // a envoye dans le DTO (voir WarehouseValidator).
    Site site = siteRepository.findById(dto.getSite().getId())
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun site avec l'ID = " + dto.getSite().getId() + " n'a ete trouve dans la BDD",
            ErrorCodes.SITE_NOT_FOUND));
    if (site.getType() != SiteType.ENTREPOT) {
      throw new InvalidEntityException("L'entrepot n'est pas valide", ErrorCodes.WAREHOUSE_NOT_VALID,
          List.of("Le site associe a un entrepot doit etre de type ENTREPOT"));
    }
    return WarehouseDto.fromEntity(
        warehouseRepository.save(WarehouseDto.toEntity(dto))
    );
  }

  @Override
  public WarehouseDto findById(Long id) {
    if (id == null) {
      log.error("Warehouse ID is null");
      return null;
    }
    return warehouseRepository.findById(id)
        .map(WarehouseDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun entrepot avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.WAREHOUSE_NOT_FOUND)
        );
  }

  @Override
  public WarehouseDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("Warehouse CODE is null");
      return null;
    }
    return warehouseRepository.findWarehouseByCode(code)
        .map(WarehouseDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun entrepot avec le CODE = " + code + " n'a ete trouve dans la BDD",
            ErrorCodes.WAREHOUSE_NOT_FOUND)
        );
  }

  @Override
  public List<WarehouseDto> findAll() {
    return warehouseRepository.findAll().stream()
        .map(WarehouseDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Warehouse ID is null");
      return;
    }
    warehouseRepository.deleteById(id);
  }
}
