package com.kfokam48.gestiondestock.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthenticationResponse {

  private String accessToken;

}
