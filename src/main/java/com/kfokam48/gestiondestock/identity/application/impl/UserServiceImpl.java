package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.identity.application.validator.UserValidator;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
import com.kfokam48.gestiondestock.identity.domain.model.User;
import com.kfokam48.gestiondestock.identity.infrastructure.persistence.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private AuthorizationService authorizationService;

  @Autowired
  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
      AuthorizationService authorizationService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.authorizationService = authorizationService;
  }

  @Override
  public UserDto save(UserDto dto) {
    // Phase 5b-2f : sans ce garde-fou, un id fourni par le client transformait cette creation en
    // MERGE JPA : la ligne utilisateur visee etait reecrite avec l'email et le mot de passe
    // choisis par l'appelant - une primitive de prise de controle de compte, d'autant que le
    // pre-check d'unicite d'email ci-dessous ne s'exclut pas lui-meme (un email neuf suffit a le
    // passer). Couvre aussi l'adaptateur legacy /utilisateurs/create, qui propage lui aussi
    // dto.getId() - voir docs/phase-5b2f-report.md.
    CreationIdGuard.requireNoId(dto.getId(), "d'un utilisateur");
    List<String> errors = UserValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("User is not valid {}", dto);
      throw new InvalidEntityException("L'utilisateur n'est pas valide", ErrorCodes.USER_NOT_VALID, errors);
    }

    if (userAlreadyExists(dto.getEmail())) {
      throw new InvalidEntityException("Un autre utilisateur avec le meme email existe deja", ErrorCodes.USER_ALREADY_EXISTS,
          Collections.singletonList("Un autre utilisateur avec le meme email existe deja dans la BDD"));
    }

    dto.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));

    return UserDto.fromEntity(
        userRepository.save(
            UserDto.toEntity(dto)
        )
    );
  }

  private boolean userAlreadyExists(String email) {
    Optional<User> user = userRepository.findUserByEmail(email);
    return user.isPresent();
  }

  @Override
  public UserDto findById(Long id) {
    if (id == null) {
      log.error("User ID is null");
      return null;
    }
    return userRepository.findById(id)
        .map(UserDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun utilisateur avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.USER_NOT_FOUND)
        );
  }

  // Phase 5b-2c : self-only via Tenant.id - meme 404 qu'un id inexistant en cas de mismatch -
  // voir docs/phase-5b2c-report.md.
  @Override
  public UserDto findById(Long id, Long callerTenantId) {
    UserDto user = findById(id);
    if (user == null || !belongsToTenant(user, callerTenantId)) {
      throw new EntityNotFoundException(
          "Aucun utilisateur avec l'ID = " + id + " n' ete trouve dans la BDD",
          ErrorCodes.USER_NOT_FOUND);
    }
    return user;
  }

  @Override
  public UserDto findByEmail(String email) {
    return userRepository.findUserByEmail(email)
        .map(UserDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun utilisateur avec l'email = " + email + " n' ete trouve dans la BDD",
            ErrorCodes.USER_NOT_FOUND)
        );
  }

  @Override
  public UserDto findByEmail(String email, Long callerTenantId) {
    UserDto user = findByEmail(email);
    if (!belongsToTenant(user, callerTenantId)) {
      throw new EntityNotFoundException(
          "Aucun utilisateur avec l'email = " + email + " n' ete trouve dans la BDD",
          ErrorCodes.USER_NOT_FOUND);
    }
    return user;
  }

  @Override
  public List<UserDto> findAll() {
    return userRepository.findAll().stream()
        .map(UserDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2c : voir docs/phase-5b2c-report.md.
  @Override
  public List<UserDto> findAll(Long callerTenantId) {
    return findAll().stream()
        .filter(user -> belongsToTenant(user, callerTenantId))
        .collect(Collectors.toList());
  }

  private boolean belongsToTenant(UserDto user, Long callerTenantId) {
    return callerTenantId != null && callerTenantId.equals(user.getIdEntreprise());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("User ID is null");
      return;
    }
    userRepository.deleteById(id);
  }

  // Phase 5b-2c : voir docs/phase-5b2c-report.md.
  @Override
  public void delete(Long id, Long callerTenantId) {
    UserDto user = findById(id, callerTenantId);
    delete(user.getId());
  }

  // Phase 5c : ordre impose - appartenance (findById scope, 404 masque) PUIS permission PUIS
  // ecriture. Inverser revient a reintroduire le contournement de 5b-2b : une affectation de
  // portee GLOBAL matche n'importe quel scopeId, donc hasPermission seul ne prouve rien.
  @Override
  public void delete(Long id, Long callerTenantId, Long userId, Long organizationId) {
    UserDto user = findById(id, callerTenantId);
    requirePermission(userId, "USER_DELETE", organizationId);
    delete(user.getId());
  }

  @Override
  public UserDto changePassword(Long id, String rawPassword) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun utilisateur n'a ete trouve avec l'ID " + id,
            ErrorCodes.USER_NOT_FOUND));

    user.setMotDePasse(passwordEncoder.encode(rawPassword));

    return UserDto.fromEntity(userRepository.save(user));
  }

  // Phase 4c : remplace SaveUtilisateurPhoto (supprime, voir docs/phase-4c-report.md). Meme motif
  // que changePassword() ci-dessus : mise a jour ciblee sur l'entite chargee, PAS un appel a
  // save(UserDto) - celui-ci re-hacherait un mot de passe deja hache (le DTO renvoye par
  // findById() porte le hash stocke, pas un mot de passe en clair) et rejetterait
  // systematiquement en doublon puisque userAlreadyExists() ne s'exclut jamais elle-meme. Bug
  // pre-existant (herite tel quel de l'ancien UtilisateurServiceImpl, jamais corrige avant),
  // elimine ici comme consequence de ne plus jamais reutiliser save() pour une mise a jour.
  @Override
  public UserDto updatePhoto(Long id, String url) {
    User user = userRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun utilisateur avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.USER_NOT_FOUND));

    user.setPhoto(url);

    return UserDto.fromEntity(userRepository.save(user));
  }
  // Phase 5c : voir docs/rbac-matrix.md.
  @Override
  public UserDto save(UserDto dto, Long callerTenantId, Long userId, Long organizationId) {
    requirePermission(userId, "USER_CREATE", organizationId);
    // Phase 5d : fail-closed. Un appelant sans tenant n'a aucun tenant ou creer, et le laisser
    // passer produirait un compte orphelin invisible aux lectures scopees.
    if (callerTenantId == null) {
      log.warn("User {} without a tenant tried to create a user", userId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action",
          ErrorCodes.USER_ACCESS_DENIED);
    }
    // Le tenant est impose depuis le principal : la valeur du corps est ecrasee, jamais lue.
    // Meme posture que /cities/filter/organization/{id} en 5b-2h (seul le principal fait foi).
    dto.setIdEntreprise(callerTenantId);
    return save(dto);
  }

  private void requirePermission(Long userId, String code, Long organizationId) {
    if (!authorizationService.hasPermission(userId, code, ScopeType.ORGANIZATION, organizationId, organizationId)) {
      log.warn("User {} lacks {} in organization {}", userId, code, organizationId);
      throw new InvalidOperationException("Vous n'avez pas la permission d'effectuer cette action",
          ErrorCodes.USER_ACCESS_DENIED);
    }
  }
}
