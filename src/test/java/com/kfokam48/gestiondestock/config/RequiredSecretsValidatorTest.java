package com.kfokam48.gestiondestock.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Phase 5b-2i : prouve que le contexte REFUSE de demarrer quand un secret manque ou est trop
 * faible. On fait tourner un contexte reduit (ApplicationContextRunner) contenant uniquement
 * {@link RequiredSecretsValidator} : c'est bien le demarrage du contexte Spring qui echoue, pas
 * une simple methode appelee a la main, et le test reste rapide (aucune base, aucun serveur).
 *
 * <p>Aucune valeur de secret reelle n'apparait ici - voir docs/phase-5b2i-report.md.
 */
public class RequiredSecretsValidatorTest {

  private static final String VALID_JWT = "x".repeat(RequiredSecretsValidator.JWT_SECRET_MIN_BYTES);

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of())
      .withUserConfiguration(RequiredSecretsValidator.class);

  @Test
  public void contextStartsWhenEverySecretIsProvided() {
    runner.withPropertyValues(
            "jwt.secret=" + VALID_JWT,
            "spring.datasource.password=whatever",
            "minio.access-key=whatever",
            "minio.secret-key=whatever")
        .run(context -> {
          assertTrue(context.getStartupFailure() == null, "le contexte doit demarrer quand tous les secrets sont fournis");
          assertNotNull(context.getBean(RequiredSecretsValidator.class));
        });
  }

  @Test
  public void contextRefusesToStartWithoutJwtSecret() {
    runner.withPropertyValues(
            "spring.datasource.password=whatever",
            "minio.access-key=whatever",
            "minio.secret-key=whatever")
        .run(context -> {
          assertNotNull(context.getStartupFailure(), "le demarrage doit echouer sans JWT_SECRET");
          assertTrue(rootMessage(context.getStartupFailure()).contains("JWT_SECRET est absent"));
        });
  }

  @Test
  public void contextRefusesToStartWithTooShortJwtSecret() {
    runner.withPropertyValues(
            "jwt.secret=too-short",
            "spring.datasource.password=whatever",
            "minio.access-key=whatever",
            "minio.secret-key=whatever")
        .run(context -> {
          assertNotNull(context.getStartupFailure(), "le demarrage doit echouer avec un secret trop court");
          assertTrue(rootMessage(context.getStartupFailure()).contains("trop court"));
        });
  }

  @Test
  public void contextRefusesToStartWithoutDatabasePasswordOrMinioKeys() {
    runner.withPropertyValues("jwt.secret=" + VALID_JWT)
        .run(context -> {
          assertNotNull(context.getStartupFailure());
          String message = rootMessage(context.getStartupFailure());
          assertTrue(message.contains("DB_PASSWORD est absent"));
          assertTrue(message.contains("MINIO_ACCESS_KEY est absent"));
          assertTrue(message.contains("MINIO_SECRET_KEY est absent"));
        });
  }

  /** Le message doit nommer les variables, jamais exposer une valeur. */
  @Test
  public void failureMessageNeverLeaksASecretValue() {
    String secretValue = "super-secret-value-that-must-not-appear";
    RequiredSecretsValidator validator =
        new RequiredSecretsValidator("too-short", secretValue, "", "");
    String message = String.join(" | ", validator.validate());
    assertTrue(message.contains("JWT_SECRET"));
    assertEquals(false, message.contains(secretValue), "aucune valeur de secret ne doit apparaitre dans le message");
  }

  private static String rootMessage(Throwable failure) {
    Throwable current = failure;
    StringBuilder all = new StringBuilder();
    while (current != null) {
      all.append(current.getMessage()).append('\n');
      current = current.getCause();
    }
    return all.toString();
  }
}
