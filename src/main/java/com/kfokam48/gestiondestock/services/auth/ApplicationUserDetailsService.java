package com.kfokam48.gestiondestock.services.auth;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Phase 4a : rebranche sur identity.User/identity.application.UserService (anciennement
// services.UtilisateurService, model.Utilisateur) - voir docs/phase-4a-report.md.
//
// Phase 5a : l'EntityNotFoundException levee par UserService.findByEmail() remontait auparavant
// telle quelle (pas enveloppee en UsernameNotFoundException), donc non attrapee par
// DaoAuthenticationProvider.retrieveUser -> 500 brut sur un email inconnu, au lieu du 400
// BAD_CREDENTIALS obtenu pour un mauvais mot de passe. Cette asymetrie constituait aussi un oracle
// d'enumeration de compte (le code HTTP seul revelait si l'email existait). Corrige en traduisant
// explicitement l'exception ici - voir docs/phase-5a-report.md.
@Service
public class ApplicationUserDetailsService implements UserDetailsService {

  private final UserService userService;

  private final UserRoleAssignmentService userRoleAssignmentService;

  @Autowired
  public ApplicationUserDetailsService(UserService userService, UserRoleAssignmentService userRoleAssignmentService) {
    this.userService = userService;
    this.userRoleAssignmentService = userRoleAssignmentService;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    UserDto user;
    try {
      user = userService.findByEmail(email);
    } catch (EntityNotFoundException ex) {
      throw new UsernameNotFoundException("Aucun utilisateur avec l'email = " + email, ex);
    }

    // Phase 17 : authorities Spring Security decoratives (aucun hasRole/@PreAuthorize dans le
    // code) — sourcees depuis identity.UserRoleAssignment.
    List<SimpleGrantedAuthority> authorities = userRoleAssignmentService.findAllByUser(user.getId()).stream()
        .map(assignment -> new SimpleGrantedAuthority(assignment.getRole().getCode()))
        .collect(Collectors.toList());

    // Un User peut exister sans Entreprise (UserValidator ne l'exige pas, et /utilisateurs/create
    // et /users/create l'autorisent reellement) : un tel utilisateur ne doit pas pour autant etre
    // incapable de s'authentifier.
    return new ExtendedUser(user.getEmail(), user.getMotDePasse(),
        user.getIdEntreprise(), user.getId(), authorities);
  }
}
