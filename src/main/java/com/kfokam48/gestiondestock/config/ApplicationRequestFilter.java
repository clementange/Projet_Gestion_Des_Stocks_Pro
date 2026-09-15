package com.kfokam48.gestiondestock.config;

import com.kfokam48.gestiondestock.services.auth.ApplicationUserDetailsService;
import com.kfokam48.gestiondestock.utils.JwtUtil;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class ApplicationRequestFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  private final ApplicationUserDetailsService userDetailsService;

  @Autowired
  public ApplicationRequestFilter(JwtUtil jwtUtil, ApplicationUserDetailsService userDetailsService) {
    this.jwtUtil = jwtUtil;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    final String authHeader = request.getHeader("Authorization");
    String userEmail = null;
    String jwt = null;
    String idEntreprise = null;

    // Un jeton malforme/altere/expire ne doit jamais faire planter la requete (500) : il doit
    // simplement echouer a authentifier, laissant la regle anyRequest().authenticated() de
    // SecurityConfiguration renvoyer un 403 propre plus loin dans la chaine. Trouve via un test
    // MockMvc envoyant un token invalide : JwtException non attrapee remontait jusqu'au filtre.
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      jwt = authHeader.substring(7);
      try {
        userEmail = jwtUtil.extractUsername(jwt);
        idEntreprise = jwtUtil.extractIdEntreprise(jwt);
      } catch (JwtException | IllegalArgumentException e) {
        log.warn("Rejected malformed or invalid JWT: {}", e.getMessage());
        jwt = null;
        userEmail = null;
      }
    }

    if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
        if (jwtUtil.validateToken(jwt, userDetails)) {
          UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
              userDetails, null, userDetails.getAuthorities()
          );
          usernamePasswordAuthenticationToken.setDetails(
              new WebAuthenticationDetailsSource().buildDetails(request)
          );
          SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
        }
      } catch (UsernameNotFoundException e) {
        log.warn("Rejected JWT for unknown user {}: {}", userEmail, e.getMessage());
      }
    }
    MDC.put("idEntreprise", idEntreprise);
    chain.doFilter(request, response);
  }
}
