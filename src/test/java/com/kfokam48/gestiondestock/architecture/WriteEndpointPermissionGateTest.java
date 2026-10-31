package com.kfokam48.gestiondestock.architecture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Phase 5c : « toute ecriture passe une permission metier ».
 *
 * <p>Meme esprit que {@code CreateEndpointIdGuardTest}, applique a la couche RBAC. Jusqu'en 5b-2h
 * l'APPARTENANCE etait verifiee partout, mais pas le DROIT : tout membre d'une organisation
 * pouvait y creer, modifier et supprimer n'importe quoi. Ce test tient l'inventaire des endpoints
 * d'ecriture en quatre temps :
 *
 * <ol>
 *   <li><b>Derive</b> : l'ensemble des endpoints d'ecriture reellement exposes (lu depuis le
 *       {@code RequestMappingHandlerMapping}, donc y compris les routes legacy declarees sur une
 *       interface {@code *Api} que de simples greps manquent) doit correspondre exactement a
 *       {@link #GATED} ∪ {@link #EXCLUDED}. Un endpoint neuf non classe fait echouer le test.</li>
 *   <li><b>Appel reel</b> : chaque classe declaree gardee appelle vraiment
 *       {@link AuthorizationService#hasPermission} (verifie sur le bytecode via ArchUnit).</li>
 *   <li><b>Bon code</b> : le code de permission declare pour un endpoint est present dans la
 *       source de la classe qui le garde. Renommer ou retirer un code fait echouer le test.</li>
 *   <li><b>Migration</b> : tout code de permission declare ici est accorde a un role par une
 *       migration Flyway. Sans ca, la permission existerait dans le code sans que personne ne
 *       puisse l'obtenir - exactement le piege de V7.</li>
 * </ol>
 *
 * <p>Limite assumee : ce test prouve la PRESENCE du controle et l'absence de DERIVE d'inventaire,
 * pas son exploitabilite. L'ordre « appartenance -> permission -> ecriture » et le refus effectif
 * d'un utilisateur du meme tenant sans permission sont prouves par
 * {@code WritePermissionEnforcementHttpIntegrationTest} ; l'isolation inter-tenant par
 * {@code CrossTenantEndpointMatrixTest}. Les trois niveaux sont complementaires - voir
 * docs/phase-5c-report.md et docs/rbac-matrix.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class WriteEndpointPermissionGateTest extends AbstractIntegrationTest {

  private static final String ROOT = "/gestiondestock/v1";
  private static final String SRC = "src/main/java/com/kfokam48/gestiondestock/";

  /** Classe qui porte le controle de permission, par endpoint garde. */
  private static final String ARTICLE = SRC + "catalog/application/impl/ArticleServiceImpl.java";
  private static final String CATEGORY = SRC + "catalog/application/impl/CategoryServiceImpl.java";
  private static final String CUSTOMER = SRC + "sales/application/impl/CustomerServiceImpl.java";
  private static final String SUPPLIER = SRC + "purchasing/application/impl/SupplierServiceImpl.java";
  private static final String CITY = SRC + "organization/application/impl/CityServiceImpl.java";
  private static final String WAREHOUSE = SRC + "organization/application/impl/WarehouseServiceImpl.java";
  private static final String SITE = SRC + "organization/application/impl/SiteServiceImpl.java";
  private static final String USER = SRC + "identity/application/impl/UserServiceImpl.java";
  private static final String SALE = SRC + "sales/application/impl/SaleServiceImpl.java";
  private static final String CUSTOMER_ORDER = SRC + "sales/application/impl/CustomerOrderServiceImpl.java";
  private static final String PURCHASE_ORDER = SRC + "purchasing/application/impl/PurchaseOrderServiceImpl.java";
  private static final String STOCK_TRANSFER = SRC + "transfers/application/impl/StockTransferServiceImpl.java";
  private static final String INVENTORY = SRC + "inventory/presentation/rest/InventoryController.java";
  private static final String URA = SRC + "identity/presentation/rest/UserRoleAssignmentController.java";

  /**
   * Endpoint d'ecriture -> (code de permission exige, source qui porte le controle).
   *
   * <p>Les deux gardes RBAC (user-role-assignments) restent dans le controleur : la ressource est
   * le catalogue RBAC lui-meme, et {@code RBAC_MANAGE} est de portee GLOBAL - decision de 5b-2c,
   * inchangee ici.
   */
  private static final Map<String, Gate> GATED = new LinkedHashMap<>();

  static {
    GATED.put("/articles/create", new Gate("ARTICLE_CREATE", ARTICLE));
    GATED.put("/articles/update/{id}", new Gate("ARTICLE_UPDATE", ARTICLE));
    GATED.put("/articles/delete/{idArticle}", new Gate("ARTICLE_DELETE", ARTICLE));
    GATED.put("/categories/create", new Gate("CATEGORY_CREATE", CATEGORY));
    GATED.put("/categories/update/{id}", new Gate("CATEGORY_UPDATE", CATEGORY));
    GATED.put("/categories/delete/{idCategory}", new Gate("CATEGORY_DELETE", CATEGORY));
    GATED.put("/customers/create", new Gate("CUSTOMER_CREATE", CUSTOMER));
    GATED.put("/customers/update/{id}", new Gate("CUSTOMER_UPDATE", CUSTOMER));
    GATED.put("/customers/delete/{id}", new Gate("CUSTOMER_DELETE", CUSTOMER));
    GATED.put("/suppliers/create", new Gate("SUPPLIER_CREATE", SUPPLIER));
    GATED.put("/suppliers/update/{id}", new Gate("SUPPLIER_UPDATE", SUPPLIER));
    GATED.put("/suppliers/delete/{id}", new Gate("SUPPLIER_DELETE", SUPPLIER));
    GATED.put("/cities/create", new Gate("CITY_CREATE", CITY));
    GATED.put("/cities/update/{id}", new Gate("CITY_UPDATE", CITY));
    GATED.put("/cities/delete/{idCity}", new Gate("CITY_DELETE", CITY));
    GATED.put("/warehouses/create", new Gate("WAREHOUSE_CREATE", WAREHOUSE));
    GATED.put("/warehouses/update/{id}", new Gate("WAREHOUSE_UPDATE", WAREHOUSE));
    GATED.put("/warehouses/delete/{idWarehouse}", new Gate("WAREHOUSE_DELETE", WAREHOUSE));
    GATED.put("/sites/create", new Gate("SITE_CREATE", SITE));
    GATED.put("/sites/delete/{idSite}", new Gate("SITE_DELETE", SITE));
    GATED.put("/users/create", new Gate("USER_CREATE", USER));
    GATED.put("/users/delete/{id}", new Gate("USER_DELETE", USER));
    GATED.put("/sales/create", new Gate("SALE_CREATE", SALE));
    GATED.put("/customer-orders/create", new Gate("CUSTOMER_ORDER_CREATE", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/valider", new Gate("CUSTOMER_ORDER_VALIDATE", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/reserver", new Gate("CUSTOMER_ORDER_RESERVE", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/preparer", new Gate("CUSTOMER_ORDER_PREPARE", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/expedier", new Gate("CUSTOMER_ORDER_SHIP", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/livrer", new Gate("CUSTOMER_ORDER_DELIVER", CUSTOMER_ORDER));
    GATED.put("/customer-orders/{idCommande}/annuler", new Gate("CUSTOMER_ORDER_CANCEL", CUSTOMER_ORDER));
    GATED.put("/purchase-orders/create", new Gate("PURCHASE_ORDER_CREATE", PURCHASE_ORDER));
    GATED.put("/purchase-orders/{idCommande}/valider", new Gate("PURCHASE_ORDER_VALIDATE", PURCHASE_ORDER));
    GATED.put("/purchase-orders/{idCommande}/annuler", new Gate("PURCHASE_ORDER_CANCEL", PURCHASE_ORDER));
    GATED.put("/purchase-orders/{idCommande}/lignes/{idLigne}/receptionner",
        new Gate("PURCHASE_ORDER_RECEIVE", PURCHASE_ORDER));
    GATED.put("/stock-transfers/create", new Gate("STOCK_TRANSFER_CREATE", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/demander", new Gate("STOCK_TRANSFER_REQUEST", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/approuver", new Gate("STOCK_TRANSFER_APPROVE", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/preparer", new Gate("STOCK_TRANSFER_PREPARE", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/expedier", new Gate("STOCK_TRANSFER_SHIP", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/receptionner", new Gate("STOCK_TRANSFER_RECEIVE", STOCK_TRANSFER));
    GATED.put("/stock-transfers/{idTransfert}/annuler", new Gate("STOCK_TRANSFER_CANCEL", STOCK_TRANSFER));
    GATED.put("/stocks/article/{idArticle}/site/{idSite}/entree", new Gate("STOCK_RECEIVE", INVENTORY));
    GATED.put("/stocks/article/{idArticle}/site/{idSite}/sortie", new Gate("STOCK_ISSUE", INVENTORY));
    GATED.put("/stocks/article/{idArticle}/site/{idSite}/correction", new Gate("STOCK_CORRECT", INVENTORY));
    GATED.put("/user-role-assignments/create", new Gate("RBAC_MANAGE", URA));
    GATED.put("/user-role-assignments/delete/{idAssignment}", new Gate("RBAC_MANAGE", URA));
  }

  /**
   * Endpoints d'ecriture SANS permission metier, chacun avec sa justification. Un endpoint ne peut
   * pas arriver ici par oubli : il faut l'y ecrire explicitement.
   */
  private static final Map<String, String> EXCLUDED = new LinkedHashMap<>();

  static {
    EXCLUDED.put("/tenants/register",
        "amorcage public d'un tenant : s'execute avant qu'aucun role n'existe, donc aucune "
            + "permission ne pourrait y etre verifiee");
    EXCLUDED.put("/tenants/delete/{id}",
        "self-only depuis 5b-2c : un tenant ne peut supprimer que lui-meme, l'identite du "
            + "principal est la seule autorisation possible");
    EXCLUDED.put("/tenants/{id}/photo", "self-only depuis 5b-2c, meme motif que tenants/delete");
    EXCLUDED.put("/organizations/delete/{idOrganization}",
        "self-only depuis 5b-2c : l'organisation du principal est la seule supprimable");
    EXCLUDED.put("/users/{id}/password",
        "self-only depuis 5a : l'appelant doit etre la cible, aucune permission d'override "
            + "admin n'a ete introduite");
    EXCLUDED.put("/users/{id}/photo", "self-only depuis 5b-2c, meme motif que users/{id}/password");
    EXCLUDED.put("/auth/authenticate",
        "point d'entree d'authentification, public par construction : c'est lui qui etablit "
            + "l'identite, aucune permission ne peut donc etre verifiee avant");
    EXCLUDED.put("/media/upload",
        "televerse un fichier et renvoie une URL : ne porte aucune ressource metier et ne "
            + "modifie aucune donnee du tenant");
    EXCLUDED.put("/articles/{idArticle}/photo",
        "appartenance verifiee (5b-2h) mais pas de permission dediee : la photo est un "
            + "sous-champ, et 5c n'a pas introduit de code *_UPDATE_PHOTO. Dette consciente, "
            + "listee en backlog de docs/phase-5c-report.md");
    EXCLUDED.put("/clients/{idClient}/photo", "meme motif que articles/{idArticle}/photo");
    EXCLUDED.put("/fournisseurs/{idFournisseur}/photo", "meme motif que articles/{idArticle}/photo");
    EXCLUDED.put("/utilisateurs/create",
        "adaptateur legacy : delegue a UserServiceImpl.save(dto) (variante sans permission). "
            + "Gap de permission assume, la route devant etre supprimee en 4e - voir "
            + "docs/phase-5c-report.md");
    EXCLUDED.put("/utilisateurs/update/password",
        "adaptateur legacy du changement de mot de passe : meme motif que /utilisateurs/create, "
            + "route devant etre supprimee en 4e");
    EXCLUDED.put("/utilisateurs/delete/{idUtilisateur}",
        "adaptateur legacy : delegue a UserService.delete(id, callerTenantId), qui verifie "
            + "l'appartenance (5b-2h) mais pas la permission. Meme motif");
  }

  /** Classes declarees gardees : verifiees une par une sur le bytecode. */
  private static final List<String> GATING_CLASSES = List.of(
      "com.kfokam48.gestiondestock.catalog.application.impl.ArticleServiceImpl",
      "com.kfokam48.gestiondestock.catalog.application.impl.CategoryServiceImpl",
      "com.kfokam48.gestiondestock.sales.application.impl.CustomerServiceImpl",
      "com.kfokam48.gestiondestock.purchasing.application.impl.SupplierServiceImpl",
      "com.kfokam48.gestiondestock.organization.application.impl.CityServiceImpl",
      "com.kfokam48.gestiondestock.organization.application.impl.WarehouseServiceImpl",
      "com.kfokam48.gestiondestock.organization.application.impl.SiteServiceImpl",
      "com.kfokam48.gestiondestock.identity.application.impl.UserServiceImpl",
      "com.kfokam48.gestiondestock.sales.application.impl.SaleServiceImpl",
      "com.kfokam48.gestiondestock.sales.application.impl.CustomerOrderServiceImpl",
      "com.kfokam48.gestiondestock.purchasing.application.impl.PurchaseOrderServiceImpl",
      "com.kfokam48.gestiondestock.transfers.application.impl.StockTransferServiceImpl",
      "com.kfokam48.gestiondestock.inventory.presentation.rest.InventoryController",
      "com.kfokam48.gestiondestock.identity.presentation.rest.UserRoleAssignmentController");

  private record Gate(String permissionCode, String sourcePath) {}

  @Autowired
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  public void everyExposedWriteEndpointIsEitherGatedOrJustifiablyExcluded() {
    Set<String> exposed = new TreeSet<>();
    handlerMapping.getHandlerMethods().forEach((info, handler) -> {
      boolean isWrite = info.getMethodsCondition().getMethods().stream()
          .anyMatch(method -> Set.of("POST", "PUT", "PATCH", "DELETE").contains(method.name()));
      if (!isWrite) {
        return;
      }
      info.getPathPatternsCondition().getPatternValues().stream()
          .filter(path -> path.startsWith(ROOT))
          .map(path -> path.substring(ROOT.length()))
          .forEach(exposed::add);
    });

    Set<String> classified = new TreeSet<>(GATED.keySet());
    classified.addAll(EXCLUDED.keySet());

    assertEquals(
        "Un endpoint d'ecriture a ete ajoute, supprime ou renomme sans mettre a jour cet "
            + "inventaire. Tout nouvel endpoint d'ecriture doit soit verifier une permission via "
            + "AuthorizationService.hasPermission et rejoindre GATED, soit etre ajoute a EXCLUDED "
            + "avec une justification explicite.",
        classified, exposed);
  }

  @Test
  public void everyClassDeclaredGatingActuallyCallsHasPermission() {
    JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.kfokam48.gestiondestock");

    for (String className : GATING_CLASSES) {
      JavaClass gating = classes.get(className);
      boolean callsHasPermission = gating.getMethodCallsFromSelf().stream()
          .anyMatch(call -> call.getTargetOwner().isEquivalentTo(AuthorizationService.class)
              && "hasPermission".equals(call.getName()));
      assertTrue(
          className + " est declare gardien d'au moins un endpoint d'ecriture mais n'appelle pas "
              + "AuthorizationService.hasPermission : soit le controle a ete retire, soit "
              + "l'inventaire GATED est faux.",
          callsHasPermission);
    }
  }

  @Test
  public void everyDeclaredPermissionCodeAppearsInTheSourceThatEnforcesIt() {
    GATED.forEach((endpoint, gate) -> {
      String source = read(Path.of(gate.sourcePath()));
      assertTrue(
          "L'endpoint " + endpoint + " est declare garde par " + gate.permissionCode() + ", mais ce "
              + "code n'apparait pas dans " + gate.sourcePath() + " : le controle a ete retire ou "
              + "le code renomme.",
          source.contains('"' + gate.permissionCode() + '"'));
    });
  }

  @Test
  public void everyDeclaredPermissionCodeIsGrantedByAFlywayMigration() {
    String migrations;
    try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
      migrations = paths.sorted()
          .map(WriteEndpointPermissionGateTest::read)
          .collect(Collectors.joining("\n"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    Set<String> codes = new TreeSet<>(GATED.values().stream()
        .map(Gate::permissionCode)
        .collect(Collectors.toSet()));
    for (String code : codes) {
      assertTrue(
          "La permission " + code + " est exigee par un endpoint d'ecriture mais aucune migration "
              + "Flyway ne l'accorde a un role : elle serait impossible a obtenir, donc "
              + "l'endpoint serait definitivement bloque (lecon de V7).",
          migrations.contains('\'' + code + '\''));
    }
  }

  @Test
  public void everyExclusionCarriesAJustification() {
    EXCLUDED.forEach((endpoint, reason) -> assertTrue(
        "L'exclusion de " + endpoint + " doit porter une justification explicite.",
        reason != null && reason.strip().length() > 30));
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("Fichier introuvable : " + path, e);
    }
  }
}
