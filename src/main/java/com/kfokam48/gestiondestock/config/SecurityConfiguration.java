package com.kfokam48.gestiondestock.config;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;
import static com.kfokam48.gestiondestock.utils.Constants.AUTHENTICATION_ENDPOINT;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, ApplicationRequestFilter applicationRequestFilter,
      CorsFilter corsFilter) throws Exception {
    http
        .addFilterBefore(corsFilter, SessionManagementFilter.class)
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                // Spring 6 PathPattern interdit "**" ailleurs qu'en fin de motif (l'ancien
                // "/**/authenticate" levait PatternParseException a la premiere requete reelle,
                // jamais detecte par les tests @SpringBootTest sans appel HTTP reel) : chemins
                // exacts a la place.
                "/" + AUTHENTICATION_ENDPOINT + "/authenticate",
                // Phase 4b : /entreprises/create a disparu (successeur : /tenants/register,
                // voir docs/phase-4b-report.md) - inscription self-service, toujours publique.
                "/" + APP_ROOT + "/tenants/register",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html").permitAll()
            .anyRequest().authenticated())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

    http.addFilterBefore(applicationRequestFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // Phase 5a : allowCredentials(true) + allowedOriginPatterns("*") est la combinaison
  // explicitement interdite par CLAUDE.md (zero-tolerance) - une origine wildcard avec
  // credentials autorise n'importe quel site tiers a rejouer un cookie/token de session. Liste
  // d'origines explicite, configurable par environnement (voir .env.example).
  @Bean
  public CorsFilter corsFilter(@Value("${cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    final CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOriginPatterns(allowedOrigins);
    config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization"));
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH"));
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
