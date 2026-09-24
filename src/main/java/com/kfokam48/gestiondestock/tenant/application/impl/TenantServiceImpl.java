package com.kfokam48.gestiondestock.tenant.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.tenant.application.validator.TenantValidator;
import com.kfokam48.gestiondestock.tenant.infrastructure.persistence.TenantRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TenantServiceImpl implements TenantService {

  private TenantRepository tenantRepository;

  @Autowired
  public TenantServiceImpl(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Override
  public TenantDto save(TenantDto dto) {
    List<String> errors = TenantValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Tenant is not valid {}", dto);
      throw new InvalidEntityException("L'entreprise n'est pas valide", ErrorCodes.TENANT_NOT_VALID, errors);
    }
    return TenantDto.fromEntity(
        tenantRepository.save(TenantDto.toEntity(dto))
    );
  }

  @Override
  public TenantDto findById(Long id) {
    if (id == null) {
      log.error("Tenant ID is null");
      return null;
    }
    return tenantRepository.findById(id)
        .map(TenantDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune entreprise avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.TENANT_NOT_FOUND)
        );
  }

  // Phase 5b-2c : self-only - meme 404 qu'un id inexistant en cas de mismatch - voir
  // docs/phase-5b2c-report.md.
  @Override
  public TenantDto findById(Long id, Long callerTenantId) {
    if (id == null || !id.equals(callerTenantId)) {
      throw new EntityNotFoundException(
          "Aucune entreprise avec l'ID = " + id + " n' ete trouve dans la BDD",
          ErrorCodes.TENANT_NOT_FOUND);
    }
    return findById(id);
  }

  // Phase 5b-2c : self-only - au plus un tenant (le sien) - voir docs/phase-5b2c-report.md.
  @Override
  public List<TenantDto> findAll(Long callerTenantId) {
    return tenantRepository.findAll().stream()
        .filter(tenant -> callerTenantId != null && callerTenantId.equals(tenant.getId()))
        .map(TenantDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id, Long callerTenantId) {
    if (id == null) {
      log.error("Tenant ID is null");
      return;
    }
    if (!id.equals(callerTenantId)) {
      throw new EntityNotFoundException(
          "Aucune entreprise avec l'ID = " + id + " n' ete trouve dans la BDD",
          ErrorCodes.TENANT_NOT_FOUND);
    }
    tenantRepository.deleteById(id);
  }

  // Phase 4c : remplace SaveEntreprisePhoto (supprime, voir docs/phase-4c-report.md). save()
  // etant deja un upsert plat sans orchestration (voir javadoc de l'interface), aucun risque de
  // redeclencher le bootstrap ici, contrairement a l'ancien EntrepriseServiceImpl.save().
  @Override
  public TenantDto updatePhoto(Long id, String url, Long callerTenantId) {
    TenantDto tenant = findById(id, callerTenantId);
    tenant.setPhoto(url);
    return save(tenant);
  }
}
