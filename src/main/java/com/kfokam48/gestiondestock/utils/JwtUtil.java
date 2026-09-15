package com.kfokam48.gestiondestock.utils;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtUtil {

  private final String secret;

  public JwtUtil(@Value("${jwt.secret}") String secret) {
    this.secret = secret;
  }

  private Key signingKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  public String extractIdEntreprise(String token) {
    final Claims claims = extractAllClaims(token);

    return claims.get("idEntreprise", String.class);
  }

  public String extractIdUtilisateur(String token) {
    final Claims claims = extractAllClaims(token);

    return claims.get("idUtilisateur", String.class);
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder().setSigningKey(signingKey()).build().parseClaimsJws(token).getBody();
  }

  private Boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  public String generateToken(ExtendedUser userDetails) {
    Map<String, Object> claims = new HashMap<>();
    return createToken(claims, userDetails);
  }

  private String createToken(Map<String, Object> claims, ExtendedUser userDetails) {

    return Jwts.builder().setClaims(claims)
        .setSubject(userDetails.getUsername())
        .setIssuedAt(new Date(System.currentTimeMillis()))
        .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10))
        .claim("idEntreprise", userDetails.getIdEntreprise() != null ? userDetails.getIdEntreprise().toString() : null)
        .claim("idUtilisateur", userDetails.getIdUtilisateur() != null ? userDetails.getIdUtilisateur().toString() : null)
        .signWith(signingKey()).compact();
  }

  public Boolean validateToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
  }

}
