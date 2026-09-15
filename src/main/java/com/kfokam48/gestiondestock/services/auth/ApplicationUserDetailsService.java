package com.kfokam48.gestiondestock.services.auth;

import com.kfokam48.gestiondestock.dto.UtilisateurDto;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.services.UtilisateurService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ApplicationUserDetailsService implements UserDetailsService {

  private final UtilisateurService service;

  private final UserRoleAssignmentService userRoleAssignmentService;

  @Autowired
  public ApplicationUserDetailsService(UtilisateurService service, UserRoleAssignmentService userRoleAssignmentService) {
    this.service = service;
    this.userRoleAssignmentService = userRoleAssignmentService;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    UtilisateurDto utilisateur = service.findByEmail(email);

    // Phase 17 : authorities Spring Security decoratives (aucun hasRole/@PreAuthorize dans le
    // code) — sourcees depuis identity.UserRoleAssignment, plus depuis le legacy Roles.
    List<SimpleGrantedAuthority> authorities = userRoleAssignmentService.findAllByUser(utilisateur.getId()).stream()
        .map(assignment -> new SimpleGrantedAuthority(assignment.getRole().getCode()))
        .collect(Collectors.toList());

    // Un Utilisateur peut exister sans Entreprise (UtilisateurValidator ne l'exige pas, et
    // /utilisateurs/create l'autorise reellement) : un tel utilisateur ne doit pas pour autant
    // etre incapable de s'authentifier.
    Long idEntreprise = utilisateur.getEntreprise() != null ? utilisateur.getEntreprise().getId() : null;

    return new ExtendedUser(utilisateur.getEmail(), utilisateur.getMoteDePasse(),
        idEntreprise, utilisateur.getId(), authorities);
  }
}
