package com.kfokam48.gestiondestock.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Socle partage pour tous les tests d'integration : demarre un unique conteneur PostgreSQL
 * (pattern "singleton container", partage par toute la JVM de test) a la place d'un Postgres
 * local suppose deja demarre a la main. {@code @DynamicPropertySource} est une fonctionnalite du
 * Spring TestContext Framework (pas une extension JUnit5) : elle fonctionne aussi bien avec les
 * classes {@code @RunWith(SpringRunner.class)} (JUnit4 vintage, utilise partout dans ce depot)
 * qu'avec du JUnit5 pur, d'ou l'absence volontaire de {@code @Testcontainers}/{@code @Container}
 * ici.
 *
 * <p>{@code spring.flyway.baseline-on-migrate=true} + {@code baseline-version=1} (voir
 * application.yml) sert a accueillir la base de dev historique deja construite a la main avant
 * l'introduction de Flyway ; sur un conteneur neuf et vide, ce reglage ferait sauter
 * V1__initial_schema.sql (la base serait "baselinee" a la version 1 sans qu'aucune table ne soit
 * creee). On le desactive donc ici pour que V1 a V5 s'executent integralement sur chaque
 * conteneur, comme sur une base CI/autre poste neuve.
 */
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("gestionstock")
          .withUsername("postgres")
          .withPassword("postgres")
          .withReuse(true);

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.baseline-on-migrate", () -> "false");
  }
}
