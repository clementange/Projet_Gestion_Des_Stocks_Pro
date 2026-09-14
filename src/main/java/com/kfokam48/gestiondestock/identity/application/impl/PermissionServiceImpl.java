package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.PermissionService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import com.kfokam48.gestiondestock.identity.application.validator.PermissionValidator;
import com.kfokam48.gestiondestock.identity.domain.model.Role;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.PermissionRepository;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.RoleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PermissionServiceImpl implements PermissionService {

  private PermissionRepository permissionRepository;
  private RoleRepository roleRepository;

  @Autowired
  public PermissionServiceImpl(PermissionRepository permissionRepository, RoleRepository roleRepository) {
    this.permissionRepository = permissionRepository;
    this.roleRepository = roleRepository;
  }

  @Override
  public PermissionDto save(PermissionDto dto) {
    List<String> errors = PermissionValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Permission is not valid {}", dto);
      throw new InvalidEntityException("La permission n'est pas valide", ErrorCodes.PERMISSION_NOT_VALID, errors);
    }
    return PermissionDto.fromEntity(
        permissionRepository.save(PermissionDto.toEntity(dto))
    );
  }

  @Override
  public PermissionDto findById(Long id) {
    if (id == null) {
      log.error("Permission ID is null");
      return null;
    }
    return permissionRepository.findById(id)
        .map(PermissionDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune permission avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.PERMISSION_NOT_FOUND)
        );
  }

  @Override
  public PermissionDto findByCode(String code) {
    return permissionRepository.findPermissionByCode(code)
        .map(PermissionDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune permission avec le CODE = " + code + " n'a ete trouvee dans la BDD",
            ErrorCodes.PERMISSION_NOT_FOUND)
        );
  }

  @Override
  public List<PermissionDto> findAll() {
    return permissionRepository.findAll().stream()
        .map(PermissionDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Permission ID is null");
      return;
    }
    List<Role> roles = roleRepository.findAllByPermissions_Id(id);
    if (!roles.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer cette permission, elle est utilisee par un ou plusieurs roles",
          ErrorCodes.PERMISSION_ALREADY_IN_USE);
    }
    permissionRepository.deleteById(id);
  }
}
