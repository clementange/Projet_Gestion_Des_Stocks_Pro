package com.kfokam48.gestiondestock.identity.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.identity.application.validator.UserValidator;
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

  @Autowired
  public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserDto save(UserDto dto) {
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
  public List<UserDto> findAll() {
    return userRepository.findAll().stream()
        .map(UserDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("User ID is null");
      return;
    }
    userRepository.deleteById(id);
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
}
