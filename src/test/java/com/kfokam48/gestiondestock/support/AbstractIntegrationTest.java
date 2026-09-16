package com.kfokam48.gestiondestock.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
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
 *
 * <p>Phase 4c : meme pattern de conteneur singleton applique a MinIO (remplace flickr), pour que
 * les tests du module {@code media} et de tout endpoint {@code /{ressource}/{id}/photo} passent
 * par un vrai stockage objet plutot que par un mock ou par le compte Flickr partage qui existait
 * avant - voir docs/phase-4c-report.md.
 */
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("gestionstock")
          .withUsername("postgres")
          .withPassword("postgres")
          .withReuse(true);

  // quay.io, pas minio/minio sur Docker Hub : MinIO Inc. a restreint l'acces anonyme a ses images
  // Docker Hub (necessite desormais une connexion) suite a un changement de licence en 2024 -
  // quay.io/minio/minio reste le miroir public gratuit. Trouve en executant les tests - voir
  // docs/phase-4c-report.md.
  static final MinIOContainer MINIO =
      new MinIOContainer(DockerImageName.parse("quay.io/minio/minio:latest").asCompatibleSubstituteFor("minio/minio"))
          .withUserName("minioadmin")
          .withPassword("minioadmin")
          .withReuse(true);

  static {
    POSTGRES.start();
    MINIO.start();
  }

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.baseline-on-migrate", () -> "false");
    registry.add("minio.endpoint", MINIO::getS3URL);
    registry.add("minio.access-key", MINIO::getUserName);
    registry.add("minio.secret-key", MINIO::getPassword);
    registry.add("minio.bucket", () -> "gestiondestock-media-test");
  }
}
