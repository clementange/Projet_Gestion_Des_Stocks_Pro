package com.kfokam48.gestiondestock.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 5b-2i : refuse le demarrage si un secret est absent ou trop faible.
 *
 * <p>Les valeurs par defaut sensibles ont ete retirees d'{@code application.yml} : les
 * placeholders resolvent desormais vers la chaine vide quand la variable d'environnement n'est pas
 * fournie. Un defaut vide (plutot qu'aucun defaut) est deliberé : il laisse le demarrage atteindre
 * cette validation, qui produit un message actionnable listant TOUTES les variables manquantes,
 * au lieu de l'erreur de placeholder non resolu de Spring, illisible et limitee a la premiere.
 *
 * <p>Aucun secret n'est jamais journalise : seuls les NOMS de variables apparaissent dans le
 * message - voir docs/phase-5b2i-report.md.
 */
@Component
public class
RequiredSecretsValidator implements InitializingBean {

  /** HS256 exige une cle d'au moins 256 bits ; en dessous, jjwt refuse de signer. */
  static final int JWT_SECRET_MIN_BYTES = 32;

  private final String jwtSecret;
  private final String databasePassword;
  private final String minioAccessKey;
  private final String minioSecretKey;

  public RequiredSecretsValidator(
      @Value("${jwt.secret:}") String jwtSecret,
      @Value("${spring.datasource.password:}") String databasePassword,
      @Value("${minio.access-key:}") String minioAccessKey,
      @Value("${minio.secret-key:}") String minioSecretKey) {
    this.jwtSecret = jwtSecret;
    this.databasePassword = databasePassword;
    this.minioAccessKey = minioAccessKey;
    this.minioSecretKey = minioSecretKey;
  }

  @Override
  public void afterPropertiesSet() {
    List<String> problems = validate();
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Demarrage refuse : la configuration des secrets est incomplete.\n  - "
              + String.join("\n  - ", problems)
              + "\nRenseignez ces variables d'environnement (voir .env.example).");
    }
  }

  /** Extrait testable : retourne la liste des problemes, sans jamais exposer de valeur. */
  List<String> validate() {
    List<String> problems = new ArrayList<>();
    if (isBlank(jwtSecret)) {
      problems.add("JWT_SECRET est absent");
    } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < JWT_SECRET_MIN_BYTES) {
      problems.add("JWT_SECRET est trop court : " + JWT_SECRET_MIN_BYTES
          + " octets minimum sont requis pour HS256");
    }
    if (isBlank(databasePassword)) {
      problems.add("DB_PASSWORD est absent");
    }
    if (isBlank(minioAccessKey)) {
      problems.add("MINIO_ACCESS_KEY est absent");
    }
    if (isBlank(minioSecretKey)) {
      problems.add("MINIO_SECRET_KEY est absent");
    }
    return problems;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
