package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.application.validator.UserRoleAssignmentValidator;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.RoleRepository;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * readOnly par defaut : findById/findAllByUser renvoient un RoleDto dont la collection
 * permissions (LAZY @ManyToMany) doit etre lue dans une session Hibernate ouverte, sinon
 * LazyInitializationException hors d'un contexte de requete HTTP (meme bug deja corrige sur
 * AuthorizationServiceImpl en Phase 4). save/delete ecrivent, donc annotes en consequence.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class UserRoleAssignmentServiceImpl implements UserRoleAssignmentService {

  private UserRoleAssignmentRepository userRoleAssignmentRepository;
  private RoleRepository roleRepository;

  @Autowired
  public UserRoleAssignmentServiceImpl(UserRoleAssignmentRepository userRoleAssignmentRepository,
      RoleRepository roleRepository) {
    this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    this.roleRepository = roleRepository;
  }

  @Override
  @Transactional
  public UserRoleAssignmentDto save(UserRoleAssignmentDto dto) {
    List<String> errors = UserRoleAssignmentValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("UserRoleAssignment is not valid {}", dto);
      throw new InvalidEntityException("L'affectation n'est pas valide", ErrorCodes.USER_ROLE_ASSIGNMENT_NOT_VALID, errors);
    }
    // Le role doit etre une reference geree : @ManyToOne n'a besoin que de l'ID pour la colonne
    // de jointure, mais on verifie ici son existence reelle plutot que de la supposer.
    roleRepository.findById(dto.getRole().getId())
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun role avec l'ID = " + dto.getRole().getId() + " n'a ete trouve dans la BDD",
            ErrorCodes.ROLE_NOT_FOUND));

    return UserRoleAssignmentDto.fromEntity(
        userRoleAssignmentRepository.save(UserRoleAssignmentDto.toEntity(dto))
    );
  }

  @Override
  public UserRoleAssignmentDto findById(Long id) {
    if (id == null) {
      log.error("UserRoleAssignment ID is null");
      return null;
    }
    return userRoleAssignmentRepository.findById(id)
        .map(UserRoleAssignmentDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune affectation avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.USER_ROLE_ASSIGNMENT_NOT_FOUND)
        );
  }

  @Override
  public List<UserRoleAssignmentDto> findAllByUser(Long userId) {
    if (userId == null) {
      log.error("User ID is null");
      return List.of();
    }
    return userRoleAssignmentRepository.findAllByUserId(userId).stream()
        .map(UserRoleAssignmentDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public void delete(Long id) {
    if (id == null) {
      log.error("UserRoleAssignment ID is null");
      return;
    }
    userRoleAssignmentRepository.deleteById(id);
  }
}
