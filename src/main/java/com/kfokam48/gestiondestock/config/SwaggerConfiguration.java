package com.kfokam48.gestiondestock.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
    name = "JWT",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = SwaggerConfiguration.AUTHORIZATION_HEADER
)
public class SwaggerConfiguration {

  public static final String AUTHORIZATION_HEADER = "Authorization";

  @Bean
  public OpenAPI api() {
    return new OpenAPI()
        .info(new Info()
            .title("Gestion de stock REST API")
            .description("Gestion de stock API documentation"));
  }
}
