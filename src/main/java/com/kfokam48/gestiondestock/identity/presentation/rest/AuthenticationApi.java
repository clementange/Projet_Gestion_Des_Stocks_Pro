package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.AUTHENTICATION_ENDPOINT;

import com.kfokam48.gestiondestock.identity.application.dto.AuthenticationRequest;
import com.kfokam48.gestiondestock.identity.application.dto.AuthenticationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "authentication")
public interface AuthenticationApi {

  @PostMapping(AUTHENTICATION_ENDPOINT + "/authenticate")
  public ResponseEntity<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request);

}
