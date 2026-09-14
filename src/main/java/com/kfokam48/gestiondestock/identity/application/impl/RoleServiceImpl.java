package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import com.kfokam48.gestiondestock.identity.application.validator.RoleValidator;
import com.kfokam48.gestiondestock.identity.domain.model.Permission;
import com.kfokam48.gestiondestock.identity.domain.model.Role;
import com.kfokam48.gestiondestock.identity.domain.model.UserRoleAssignment;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.PermissionRepository;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.RoleRepository;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * readOnly par defaut : findById/findByCode/findAll renvoient un RoleDto dont la collection
 * permissions (LAZY @ManyToMany) doit etre lue dans une session Hibernate ouverte, sinon
 * LazyInitializationException hors d'un contexte de requete HTTP (meme bug deja corrige sur
 * AuthorizationServiceImpl en Phase 4). save/delete ecrivent, donc annotes en consequence.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class RoleServiceImpl implements RoleService {

  private RoleRepository roleRepository;
  private PermissionRepository permissionRepository;
  private UserRoleAssignmentRepository userRoleAssignmentRepository;

  @Autowired
  public RoleServiceImpl(RoleRepository roleRepository, PermissionRepository permissionRepository,
      UserRoleAssignmentRepository userRoleAssignmentRepository) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.userRoleAssignmentRepository = userRoleAssignmentRepository;
  }

  @Override
  @Transactional
  public RoleDto save(RoleDto dto) {
    List<String> errors = RoleValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Role is not valid {}", dto);
      throw new InvalidEntityException("Le role n'est pas valide", ErrorCodes.ROLE_NOT_VALID, errors);
    }

    Role role = RoleDto.toEntity(dto);
    role.setPermissions(resolveManagedPermissions(dto.getPermissions()));

    return RoleDto.fromEntity(roleRepository.save(role));
  }

  /**
   * Une collection @ManyToMany doit etre peuplee avec des entites gerees par Hibernate pour que
   * la table de jointure soit ecrite correctement, contrairement a un simple @ManyToOne ou seul
   * l'ID compte.
   */
  private Set<Permission> resolveManagedPermissions(Set<PermissionDto> permissionDtos) {
    if (permissionDtos == null || permissionDtos.isEmpty()) {
      return new HashSet<>();
    }
    List<Long> ids = permissionDtos.stream()
        .map(PermissionDto::getId)
        .collect(Collectors.toList());
    return new HashSet<>(permissionRepository.findAllById(ids));
  }

  @Override
  public RoleDto findById(Long id) {
    if (id == null) {
      log.error("Role ID is null");
      return null;
    }
    return roleRepository.findById(id)
        .map(RoleDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun role avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.ROLE_NOT_FOUND)
        );
  }

  @Override
  public RoleDto findByCode(String code) {
    return roleRepository.findRoleByCode(code)
        .map(RoleDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun role avec le CODE = " + code + " n'a ete trouve dans la BDD",
            ErrorCodes.ROLE_NOT_FOUND)
        );
  }

  @Override
  public List<RoleDto> findAll() {
    return roleRepository.findAll().stream()
        .map(RoleDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (id == null) {
      log.error("Role ID is null");
      return;
    }
    List<UserRoleAssignment> assignments = userRoleAssignmentRepository.findAllByRoleId(id);
    if (!assignments.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer ce role, il est affecte a un ou plusieurs utilisateurs",
          ErrorCodes.ROLE_ALREADY_IN_USE);
    }
    roleRepository.deleteById(id);
  }
}
