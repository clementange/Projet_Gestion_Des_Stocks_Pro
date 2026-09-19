package com.kfokam48.gestiondestock.model.auth;

import java.util.Collection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class ExtendedUser extends User {
  @Getter
  @Setter
  private Long idEntreprise;

  @Getter
  @Setter
  private Long idUtilisateur;

  // Phase 5b-1 : organisation de l'utilisateur (organization.Organization.id, pas Tenant.id comme
  // idEntreprise) - resolue une fois au login, portee par le JWT au meme titre qu'idEntreprise/
  // idUtilisateur. Necessaire pour qu'AuthorizationService.hasPermission puisse verifier qu'une
  // affectation RBAC appartient bien a l'organisation de l'appelant. Voir docs/phase-5b1-report.md.
  @Getter
  @Setter
  private Long organizationId;

  public ExtendedUser(String username, String password,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
  }

  public ExtendedUser(String username, String password, Long idEntreprise,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
    this.idEntreprise = idEntreprise;
  }

  public ExtendedUser(String username, String password, Long idEntreprise, Long idUtilisateur,
      Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
    this.idEntreprise = idEntreprise;
    this.idUtilisateur = idUtilisateur;
  }

  public ExtendedUser(String username, String password, Long idEntreprise, Long idUtilisateur,
      Long organizationId, Collection<? extends GrantedAuthority> authorities) {
    super(username, password, authorities);
    this.idEntreprise = idEntreprise;
    this.idUtilisateur = idUtilisateur;
    this.organizationId = organizationId;
  }
}
