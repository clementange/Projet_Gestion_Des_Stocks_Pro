package com.kfokam48.gestiondestock.identity.presentation.rest;


import com.kfokam48.gestiondestock.identity.presentation.rest.AuthenticationApi;
import com.kfokam48.gestiondestock.identity.application.dto.AuthenticationRequest;
import com.kfokam48.gestiondestock.identity.application.dto.AuthenticationResponse;
import com.kfokam48.gestiondestock.shared.ExtendedUser;
import com.kfokam48.gestiondestock.config.ApplicationUserDetailsService;
import com.kfokam48.gestiondestock.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController implements AuthenticationApi {

  private final AuthenticationManager authenticationManager;

  private final ApplicationUserDetailsService userDetailsService;

  private final JwtUtil jwtUtil;

  @Autowired
  public AuthenticationController(AuthenticationManager authenticationManager,
      ApplicationUserDetailsService userDetailsService, JwtUtil jwtUtil) {
    this.authenticationManager = authenticationManager;
    this.userDetailsService = userDetailsService;
    this.jwtUtil = jwtUtil;
  }

  @Override
  public ResponseEntity<AuthenticationResponse> authenticate(AuthenticationRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getLogin(),
            request.getPassword()
        )
    );
    final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getLogin());

    final String jwt = jwtUtil.generateToken((ExtendedUser) userDetails);

    return ResponseEntity.ok(AuthenticationResponse.builder().accessToken(jwt).build());
  }

}
