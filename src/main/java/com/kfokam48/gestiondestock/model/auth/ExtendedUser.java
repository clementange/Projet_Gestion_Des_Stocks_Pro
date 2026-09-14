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
}
