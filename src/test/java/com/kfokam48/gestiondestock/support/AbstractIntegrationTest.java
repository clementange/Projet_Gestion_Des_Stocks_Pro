package com.kfokam48.gestiondestock.support;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.identity.application.PermissionService;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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

  /**
   * Phase 5c : toutes les ecritures exigent desormais une permission metier. Les tests qui
   * CONSTRUISENT un decor (creer une ville, un site, un article...) ont besoin d'un utilisateur
   * autorise ; ceux qui VERIFIENT un refus continuent de se forger un utilisateur aux permissions
   * precises (voir {@code grantOrderPermissions} dans les tests d'agregats). Ce helper ne sert
   * donc qu'au decor - voir docs/phase-5c-report.md.
   *
   * <p>Les trois services sont {@code required = false} : les sous-classes qui ne demarrent pas de
   * contexte Spring complet n'en ont pas besoin.
   */
  private static final List<String> ALL_WRITE_PERMISSIONS = List.of(
      "ARTICLE_CREATE", "ARTICLE_UPDATE", "ARTICLE_DELETE",
      "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE",
      "CUSTOMER_CREATE", "CUSTOMER_UPDATE", "CUSTOMER_DELETE",
      "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DELETE",
      "CITY_CREATE", "CITY_UPDATE", "CITY_DELETE",
      "SITE_CREATE", "SITE_DELETE",
      "WAREHOUSE_CREATE", "WAREHOUSE_UPDATE", "WAREHOUSE_DELETE",
      "USER_CREATE", "USER_DELETE",
      "SALE_CREATE",
      "CUSTOMER_ORDER_CREATE", "CUSTOMER_ORDER_VALIDATE", "CUSTOMER_ORDER_RESERVE",
      "CUSTOMER_ORDER_PREPARE", "CUSTOMER_ORDER_SHIP", "CUSTOMER_ORDER_DELIVER",
      "CUSTOMER_ORDER_CANCEL",
      "PURCHASE_ORDER_CREATE", "PURCHASE_ORDER_VALIDATE", "PURCHASE_ORDER_CANCEL",
      "PURCHASE_ORDER_RECEIVE",
      "STOCK_TRANSFER_CREATE", "STOCK_TRANSFER_REQUEST", "STOCK_TRANSFER_APPROVE",
      "STOCK_TRANSFER_PREPARE", "STOCK_TRANSFER_SHIP", "STOCK_TRANSFER_RECEIVE",
      "STOCK_TRANSFER_CANCEL",
      "STOCK_RECEIVE", "STOCK_ISSUE", "STOCK_CORRECT");

  @Autowired(required = false)
  private PermissionService permissionServiceForFixtures;

  @Autowired(required = false)
  private RoleService roleServiceForFixtures;

  @Autowired(required = false)
  private UserRoleAssignmentService assignmentServiceForFixtures;

  /**
   * Un utilisateur de l'organisation donnee, porteur de toutes les permissions d'ecriture via une
   * affectation de portee {@code GLOBAL} dans CETTE organisation - exactement la forme que prend
   * l'affectation de l'administrateur a l'amorcage d'un tenant. La portee GLOBAL matche n'importe
   * quel {@code scopeId} demande, mais seulement a l'interieur de l'organisation de l'affectation :
   * ce helper ne peut donc pas masquer une faille d'isolation inter-tenant.
   */
  protected Long userWithAllWritePermissions(Long organizationId) {
    return fixtureUsersByOrganization.computeIfAbsent(organizationId,
        this::createUserWithAllWritePermissions);
  }

  // Memoise par organisation : un decor appelle ce helper a chaque creation de ressource, et
  // recreer un role de 44 permissions a chaque fois rendrait les tests inutilement lents.
  private final java.util.Map<Long, Long> fixtureUsersByOrganization = new java.util.HashMap<>();

  private Long createUserWithAllWritePermissions(Long organizationId) {
    Set<PermissionDto> permissions = ALL_WRITE_PERMISSIONS.stream().map(code -> {
      try {
        return permissionServiceForFixtures.findByCode(code);
      } catch (EntityNotFoundException e) {
        return permissionServiceForFixtures.save(
            PermissionDto.builder().code(code).description(code).build());
      }
    }).collect(Collectors.toSet());
    RoleDto role = roleServiceForFixtures.save(RoleDto.builder()
        .code("ROLE-FIXTURE-" + UUID.randomUUID().toString().substring(0, 8))
        .name("Decor de test (toutes les ecritures)")
        .permissions(permissions)
        .build());
    Long userId = ThreadLocalRandom.current().nextLong(1_000_000L, 9_000_000L);
    assignmentServiceForFixtures.save(UserRoleAssignmentDto.builder()
        .userId(userId).role(role).scopeType(ScopeType.GLOBAL)
        .organizationId(organizationId).build());
    return userId;
  }
}
