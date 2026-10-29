package com.kfokam48.gestiondestock.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Phase 5b-2f : « un create ne peut jamais etre un update ».
 *
 * <p>Tous les {@code toEntity} des DTO applicatifs propagent {@code dto.getId()}, et
 * {@code repository.save()} sur une entite portant un id existant est un MERGE JPA. Tout endpoint
 * de creation qui laisse passer cet id devient donc un endpoint de mise a jour deguise, exploitable
 * d'un tenant a l'autre. Ce test tient l'inventaire de ces endpoints en deux temps :
 *
 * <ol>
 *   <li><b>Derive</b> : l'ensemble des {@code POST .../create} reellement exposes (lu depuis le
 *       {@code RequestMappingHandlerMapping}, donc y compris les routes legacy declarees sur une
 *       interface {@code *Api} que de simples greps manquent) doit correspondre exactement a
 *       l'inventaire ci-dessous. Un endpoint neuf non classe fait echouer le test : il force une
 *       decision explicite plutot qu'un oubli silencieux.</li>
 *   <li><b>Application</b> : chaque service declare protege doit reellement appeler
 *       {@link CreationIdGuard#requireNoId(Long, String)} (verifie sur le bytecode via ArchUnit).
 *       Retirer le garde-fou fait echouer le test.</li>
 * </ol>
 *
 * <p>Limite assumee et explicite : la partie 1 detecte la DERIVE d'inventaire, elle ne prouve pas
 * l'absence de faille. Prouver l'exploitabilite demande un payload valide propre a chaque endpoint,
 * ce que font les tests d'integration dedies
 * ({@code CreateEndpointIdInjectionHttpIntegrationTest},
 * {@code OrganizationControllersHttpIntegrationTest}). Les deux niveaux sont complementaires -
 * voir docs/phase-5b2f-report.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class CreateEndpointIdGuardTest extends AbstractIntegrationTest {

  /** Endpoints de creation dont le service rejette explicitement un id fourni par le client. */
  private static final Set<String> GUARDED = new TreeSet<>(List.of(
      "/gestiondestock/v1/sites/create",
      "/gestiondestock/v1/users/create",
      // Adaptateur legacy : delegue a UserServiceImpl.save, donc couvert par le meme garde-fou.
      "/gestiondestock/v1/utilisateurs/create",
      "/gestiondestock/v1/user-role-assignments/create"));

  /**
   * Endpoints encore vulnerables au meme vecteur, hors perimetre de 5b-2f, audites et documentes
   * dans docs/phase-5b2f-report.md §3. Cette liste est une DETTE assumee, pas une absolution :
   * elle doit diminuer, jamais grandir.
   */
  private static final Set<String> KNOWN_UNGUARDED_DEBT = new TreeSet<>(List.of(
      "/gestiondestock/v1/articles/create",
      "/gestiondestock/v1/categories/create",
      "/gestiondestock/v1/cities/create",
      "/gestiondestock/v1/customer-orders/create",
      "/gestiondestock/v1/customers/create",
      "/gestiondestock/v1/purchase-orders/create",
      "/gestiondestock/v1/sales/create",
      "/gestiondestock/v1/stock-transfers/create",
      "/gestiondestock/v1/suppliers/create",
      "/gestiondestock/v1/warehouses/create"));

  /** Services declares proteges, verifies un par un sur le bytecode (partie 2). */
  private static final List<String> GUARDED_SERVICES = List.of(
      "com.kfokam48.gestiondestock.organization.application.impl.SiteServiceImpl",
      "com.kfokam48.gestiondestock.identity.application.impl.UserServiceImpl",
      "com.kfokam48.gestiondestock.identity.application.impl.UserRoleAssignmentServiceImpl");

  @Autowired
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  public void everyExposedCreateEndpointIsClassifiedAsGuardedOrKnownDebt() {
    Set<String> exposed = new TreeSet<>();
    handlerMapping.getHandlerMethods().forEach((info, handler) -> {
      boolean isPost = info.getMethodsCondition().getMethods().stream()
          .anyMatch(method -> "POST".equals(method.name()));
      if (!isPost) {
        return;
      }
      info.getPathPatternsCondition().getPatternValues().stream()
          .filter(path -> path.endsWith("/create"))
          .forEach(exposed::add);
    });

    Set<String> classified = new TreeSet<>(GUARDED);
    classified.addAll(KNOWN_UNGUARDED_DEBT);

    assertEquals(
        "Un POST .../create a ete ajoute, supprime ou renomme sans mettre a jour cet inventaire. "
            + "Tout nouvel endpoint de creation doit soit appeler CreationIdGuard.requireNoId et "
            + "rejoindre GUARDED, soit etre ajoute sciemment a KNOWN_UNGUARDED_DEBT avec une "
            + "justification dans docs/phase-5b2f-report.md.",
        classified, exposed);
  }

  @Test
  public void everyServiceDeclaredGuardedActuallyCallsTheCentralGuard() {
    JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.kfokam48.gestiondestock");

    for (String serviceName : GUARDED_SERVICES) {
      JavaClass service = classes.get(serviceName);
      boolean callsGuard = service.getMethodCallsFromSelf().stream()
          .anyMatch(call -> call.getTargetOwner().isEquivalentTo(CreationIdGuard.class)
              && "requireNoId".equals(call.getName()));
      assertTrue(
          serviceName + " est declare protege mais n'appelle pas CreationIdGuard.requireNoId : "
              + "soit le garde-fou a ete retire, soit l'inventaire GUARDED est faux.",
          callsGuard);
    }
  }
}
