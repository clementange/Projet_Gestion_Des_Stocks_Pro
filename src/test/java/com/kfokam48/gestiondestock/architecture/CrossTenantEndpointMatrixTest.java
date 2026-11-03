package com.kfokam48.gestiondestock.architecture;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 5b-2h — matrice inter-tenant automatique.
 *
 * <p>Enumere TOUS les handlers HTTP depuis le {@code RequestMappingHandlerMapping}, cree deux
 * tenants A et B avec un jeu de ressources chez A, puis, pour chaque endpoint portant un id de
 * ressource, appelle avec le jeton de B et les ids de A. Chaque appel doit echouer (4xx) ; et pour
 * les ecritures, on verifie en plus que la ressource de A est toujours lisible par A, inchangee -
 * l'absence d'effet de bord observable.
 *
 * <p>Tout endpoint qui n'est ni couvert par la matrice ni explicitement classe « sans id de
 * ressource » fait echouer {@link #everyEndpointIsEitherProbedOrJustifiablyExcluded()} : un
 * endpoint neuf ne peut pas passer inapercu.
 *
 * <p>Limite assumee : la matrice prouve qu'un appel inter-tenant est refuse, pas que l'endpoint est
 * exempt de tout autre defaut (permission metier, validation). Voir docs/phase-5b2h-report.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CrossTenantEndpointMatrixTest extends AbstractIntegrationTest {

  /**
   * Endpoints qui ne portent aucun id de ressource d'un autre tenant, donc hors matrice.
   * Chaque entree porte sa justification : l'exclusion doit etre un choix, pas un oubli.
   */
  private static final Map<String, String> EXCLUDED = new LinkedHashMap<>();

  static {
    EXCLUDED.put("/gestiondestock/v1/auth/authenticate", "public, aucune ressource en parametre");
    EXCLUDED.put("/gestiondestock/v1/tenants/register", "public, cree le tenant appelant lui-meme");
    EXCLUDED.put("/gestiondestock/v1/media/upload", "upload binaire, aucun id de ressource");
    // Listes auto-scopees : elles ne prennent aucun id, et leur filtrage par organisation est
    // couvert par les tests dedies des phases 5b-2a a 5b-2c.
    EXCLUDED.put("/gestiondestock/v1/articles/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/categories/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/customers/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/suppliers/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/sales/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/customer-orders/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/purchase-orders/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/stock-transfers/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/sites/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/warehouses/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/users/all", "liste auto-scopee, aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/organizations/all", "self-only (5b-2c), aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/tenants/all", "self-only (5b-2c), aucun id en entree");
    EXCLUDED.put("/gestiondestock/v1/roles/all", "catalogue GLOBAL, gate RBAC_MANAGE (5b-2f)");
    EXCLUDED.put("/gestiondestock/v1/permissions/all", "catalogue GLOBAL, gate RBAC_MANAGE (5b-2f)");
    // Creations : l'injection d'id y est deja couverte exhaustivement par
    // CreateIdInjectionAllEndpointsHttpIntegrationTest et CreateEndpointIdGuardTest (5b-2f/5b-2g).
    EXCLUDED.put("/gestiondestock/v1/articles/create", "creation : couverte par les tests d'injection d'id (5b-2g)");
    EXCLUDED.put("/gestiondestock/v1/categories/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/cities/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/warehouses/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/customers/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/suppliers/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/sales/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/customer-orders/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/purchase-orders/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/stock-transfers/create", "creation : idem 5b-2g");
    EXCLUDED.put("/gestiondestock/v1/sites/create", "creation : idem 5b-2f");
    EXCLUDED.put("/gestiondestock/v1/users/create", "creation : idem 5b-2f");
    EXCLUDED.put("/gestiondestock/v1/user-role-assignments/create", "creation : idem 5b-2f");
    EXCLUDED.put("/gestiondestock/v1/organizations/create", "supprime en 5b-2e (n'existe plus)");
    // Mot de passe / photo de soi-meme : self-only, couvert par 5a et 5b-2c.
    EXCLUDED.put("/gestiondestock/v1/users/{id}/password", "self-only (5a), id = l'appelant");
    EXCLUDED.put("/gestiondestock/v1/users/{id}/photo", "self-only (5b-2c), id = l'appelant");
    EXCLUDED.put("/gestiondestock/v1/roles/{idRole}", "catalogue GLOBAL, gate RBAC_MANAGE (5b-2f)");
    EXCLUDED.put("/gestiondestock/v1/permissions/{idPermission}", "catalogue GLOBAL, gate RBAC_MANAGE (5b-2f)");
    // Phase 5g : exclusion CONSERVEE mais rejustifiee. Une sonde y serait trompeuse : aucun
    // endpoint n'ecrit seuilAlerte, donc la reponse est toujours vide et la sonde passerait sans
    // rien prouver. Le filtrage d'organisation de cet endpoint est verifie par
    // reporting.GlobalStockReportIsScopedToOrganizationTest, qui pose le seuil en base.
    EXCLUDED.put("/gestiondestock/v1/reporting/stock/low",
        "liste scopee par organisation en SQL (5g) ; contenu non sondable ici, seuilAlerte n'etant "
            + "ecrit par aucun endpoint - verifie par GlobalStockReportIsScopedToOrganizationTest");
    EXCLUDED.put("/gestiondestock/v1/swagger-ui.html", "documentation");
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

  private static String uniq(String p) {
    return p + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static final class Tenant {
    String token;
    long orgId;
    long tenantId;
    long userId;
    long cityId;
    long siteId;
    long otherSiteId;
    long categoryId;
    long articleId;
    long warehouseId;
    long customerId;
    long supplierId;
    long saleId;
    long customerOrderId;
    long purchaseOrderId;
    long stockTransferId;
    long assignmentId;
    String articleCode;
    String categoryCode;
    String siteCode;
    String warehouseCode;
    String saleCode;
    String customerOrderCode;
    String purchaseOrderCode;
    String stockTransferCode;
    String cityName;
  }

  private String body(String url, String token, String json) throws Exception {
    return mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private long id(String json) throws Exception {
    return objectMapper.readTree(json).get("id").asLong();
  }

  private Tenant provision() throws Exception {
    Tenant t = new Tenant();
    String email = uniq("mx") + "@test.local";
    TenantRegistrationRequest reg = new TenantRegistrationRequest(
        "Societe Matrix", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000090", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)))
        .andExpect(status().isOk());
    MvcResult login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}"))
        .andExpect(status().isOk()).andReturn();
    t.token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    var claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(t.token.split("\\.")[1]));
    t.orgId = claims.get("organizationId").asLong();
    t.tenantId = claims.get("idEntreprise").asLong();
    t.userId = claims.get("idUtilisateur").asLong();

    // Nom unique : il sert de marqueur fiable pour detecter une ville d'un autre tenant dans une
    // reponse, la ou un id numerique se confondrait avec un id imbrique quelconque.
    t.cityName = uniq("Ville");
    t.cityId = id(body("/gestiondestock/v1/cities/create", t.token,
        "{\"name\":\"" + t.cityName + "\",\"organization\":{\"id\":" + t.orgId + "}}"));
    t.siteCode = uniq("S");
    t.siteId = id(body("/gestiondestock/v1/sites/create", t.token,
        "{\"code\":\"" + t.siteCode + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + t.cityId + "}}"));
    t.otherSiteId = id(body("/gestiondestock/v1/sites/create", t.token,
        "{\"code\":\"" + uniq("S2") + "\",\"name\":\"Entrepot2\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + t.cityId + "}}"));
    t.warehouseCode = uniq("WH");
    t.warehouseId = id(body("/gestiondestock/v1/warehouses/create", t.token,
        "{\"code\":\"" + t.warehouseCode + "\",\"name\":\"WH\",\"active\":true,\"site\":{\"id\":" + t.siteId + "}}"));
    t.categoryCode = uniq("C");
    t.categoryId = id(body("/gestiondestock/v1/categories/create", t.token,
        "{\"code\":\"" + t.categoryCode + "\",\"designation\":\"Cat\"}"));
    t.articleCode = uniq("A");
    t.articleId = id(body("/gestiondestock/v1/articles/create", t.token,
        "{\"codeArticle\":\"" + t.articleCode + "\",\"designation\":\"Art\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + t.categoryId + "}}"));
    t.customerId = id(body("/gestiondestock/v1/customers/create", t.token,
        "{\"nom\":\"Cli\",\"prenom\":\"P\",\"mail\":\"" + uniq("cli") + "@test.local\",\"numTel\":\"+237600000001\","
            + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    t.supplierId = id(body("/gestiondestock/v1/suppliers/create", t.token,
        "{\"nom\":\"Fou\",\"prenom\":\"P\",\"mail\":\"" + uniq("fou") + "@test.local\",\"numTel\":\"+237600000002\","
            + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    body("/gestiondestock/v1/stocks/article/" + t.articleId + "/site/" + t.siteId
        + "/entree?quantite=100&reference=INIT", t.token, "");
    t.saleCode = uniq("VEN");
    t.saleId = id(body("/gestiondestock/v1/sales/create", t.token,
        "{\"sale\":{\"code\":\"" + t.saleCode + "\",\"site\":{\"id\":" + t.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + t.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"));
    t.customerOrderCode = uniq("CMD");
    t.customerOrderId = id(body("/gestiondestock/v1/customer-orders/create", t.token,
        "{\"order\":{\"code\":\"" + t.customerOrderCode + "\",\"customerId\":" + t.customerId + ",\"site\":{\"id\":" + t.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + t.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"));
    t.purchaseOrderCode = uniq("PO");
    t.purchaseOrderId = id(body("/gestiondestock/v1/purchase-orders/create", t.token,
        "{\"order\":{\"code\":\"" + t.purchaseOrderCode + "\",\"supplierId\":" + t.supplierId + ",\"site\":{\"id\":" + t.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + t.articleId + "},\"quantiteCommandee\":1,\"prixUnitaire\":10}]}"));
    t.stockTransferCode = uniq("TR");
    t.stockTransferId = id(body("/gestiondestock/v1/stock-transfers/create", t.token,
        "{\"transfer\":{\"code\":\"" + t.stockTransferCode + "\",\"originSite\":{\"id\":" + t.siteId + "},"
            + "\"destinationSite\":{\"id\":" + t.otherSiteId + "},\"requestedByUserId\":" + t.userId + "},"
            + "\"lines\":[{\"article\":{\"id\":" + t.articleId + "},\"quantite\":1}]}"));
    MvcResult ura = mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/filter/user/" + t.userId)
            .header("Authorization", "Bearer " + t.token)).andExpect(status().isOk()).andReturn();
    t.assignmentId = objectMapper.readTree(ura.getResponse().getContentAsString()).get(0).get("id").asLong();
    return t;
  }

  /** Une sonde = un appel inter-tenant attendu en echec. */
  /**
   * Mode attendu pour un appel inter-tenant. {@code DENY} : l'appel doit echouer (4xx) — tout
   * acces par id. {@code EMPTY} : l'endpoint est une LISTE filtree par organisation, donc repondre
   * 200 avec une liste vide est correct et non une fuite ; on exige 200 + corps vide, un corps
   * non vide etant la fuite.
   */
  private enum Mode { DENY, EMPTY, NO_FOREIGN_DATA }

  private record Probe(String method, String pattern, String url, String json, Mode mode) {
    Probe(String method, String pattern, String url, String json) {
      this(method, pattern, url, json, Mode.DENY);
    }
  }

  private List<Probe> probes(Tenant a, Tenant b) {
    List<Probe> p = new ArrayList<>();
    String v = "/gestiondestock/v1";
    // --- lectures par id
    p.add(new Probe("GET", v + "/articles/{idArticle}", v + "/articles/" + a.articleId, null));
    p.add(new Probe("GET", v + "/categories/{idCategory}", v + "/categories/" + a.categoryId, null));
    p.add(new Probe("GET", v + "/cities/{idCity}", v + "/cities/" + a.cityId, null));
    p.add(new Probe("GET", v + "/sites/{idSite}", v + "/sites/" + a.siteId, null));
    p.add(new Probe("GET", v + "/warehouses/{idWarehouse}", v + "/warehouses/" + a.warehouseId, null));
    p.add(new Probe("GET", v + "/customers/{id}", v + "/customers/" + a.customerId, null));
    p.add(new Probe("GET", v + "/suppliers/{id}", v + "/suppliers/" + a.supplierId, null));
    p.add(new Probe("GET", v + "/sales/{idVente}", v + "/sales/" + a.saleId, null));
    p.add(new Probe("GET", v + "/sales/{idVente}/lignes", v + "/sales/" + a.saleId + "/lignes", null));
    p.add(new Probe("GET", v + "/customer-orders/{idCommande}", v + "/customer-orders/" + a.customerOrderId, null));
    p.add(new Probe("GET", v + "/customer-orders/{idCommande}/lignes", v + "/customer-orders/" + a.customerOrderId + "/lignes", null));
    p.add(new Probe("GET", v + "/purchase-orders/{idCommande}", v + "/purchase-orders/" + a.purchaseOrderId, null));
    p.add(new Probe("GET", v + "/purchase-orders/{idCommande}/lignes", v + "/purchase-orders/" + a.purchaseOrderId + "/lignes", null));
    p.add(new Probe("GET", v + "/stock-transfers/{idTransfert}", v + "/stock-transfers/" + a.stockTransferId, null));
    p.add(new Probe("GET", v + "/stock-transfers/{idTransfert}/lignes", v + "/stock-transfers/" + a.stockTransferId + "/lignes", null));
    p.add(new Probe("GET", v + "/users/{id}", v + "/users/" + a.userId, null));
    p.add(new Probe("GET", v + "/organizations/{idOrganization}", v + "/organizations/" + a.orgId, null));
    p.add(new Probe("GET", v + "/tenants/{id}", v + "/tenants/" + a.tenantId, null));
    p.add(new Probe("GET", v + "/user-role-assignments/{idAssignment}", v + "/user-role-assignments/" + a.assignmentId, null));
    p.add(new Probe("GET", v + "/user-role-assignments/filter/user/{idUser}", v + "/user-role-assignments/filter/user/" + a.userId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/sites/filter/city/{idCity}", v + "/sites/filter/city/" + a.cityId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/cities/filter/organization/{idOrganization}", v + "/cities/filter/organization/" + a.orgId, a.cityName, Mode.NO_FOREIGN_DATA));
    // Phase 5g : cet endpoint etait EXCLU avec la justification « filtre par RBAC (5b-1) »,
    // qui etait fausse - le RBAC decidait du DROIT de lire, jamais du PERIMETRE lu, et le rapport
    // renvoyait le stock de tous les tenants. Sonde de CONTENU (pas seulement de statut) : B
    // appelle son propre rapport global, le code article de A ne doit pas y figurer.
    p.add(new Probe("GET", v + "/reporting/stock/global", v + "/reporting/stock/global",
        a.articleCode, Mode.NO_FOREIGN_DATA));
    p.add(new Probe("GET", v + "/articles/filter/category/{idCategory}", v + "/articles/filter/category/" + a.categoryId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/articles/historique/vente/{idArticle}", v + "/articles/historique/vente/" + a.articleId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/articles/historique/commandeclient/{idArticle}", v + "/articles/historique/commandeclient/" + a.articleId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/articles/historique/commandefournisseur/{idArticle}", v + "/articles/historique/commandefournisseur/" + a.articleId, null, Mode.EMPTY));
    p.add(new Probe("GET", v + "/reporting/stock/site/{idSite}", v + "/reporting/stock/site/" + a.siteId, null));
    p.add(new Probe("GET", v + "/reporting/sales/site/{idSite}", v + "/reporting/sales/site/" + a.siteId + "?from=2020-01-01T00:00:00Z&to=2030-01-01T00:00:00Z", null));
    p.add(new Probe("GET", v + "/stocks/article/{idArticle}/site/{idSite}", v + "/stocks/article/" + a.articleId + "/site/" + a.siteId, null));
    p.add(new Probe("GET", v + "/stocks/article/{idArticle}/site/{idSite}/mouvements", v + "/stocks/article/" + a.articleId + "/site/" + a.siteId + "/mouvements", null));
    // lectures par code : le code de A n'est pas devinable, mais l'endpoint doit rester scope
    p.add(new Probe("GET", v + "/users/find/{email}", v + "/users/find/" + uniq("nobody") + "@x.test", null));

    // lookups par code : le code de A est connu de la fixture, donc reellement exploitable
    p.add(new Probe("GET", v + "/articles/filter/{codeArticle}", v + "/articles/filter/" + a.articleCode, null));
    p.add(new Probe("GET", v + "/categories/filter/{codeCategory}", v + "/categories/filter/" + a.categoryCode, null));
    p.add(new Probe("GET", v + "/sites/filter/{codeSite}", v + "/sites/filter/" + a.siteCode, null));
    p.add(new Probe("GET", v + "/warehouses/filter/{codeWarehouse}", v + "/warehouses/filter/" + a.warehouseCode, null));
    p.add(new Probe("GET", v + "/sales/filter/{code}", v + "/sales/filter/" + a.saleCode, null));
    p.add(new Probe("GET", v + "/customer-orders/filter/{code}", v + "/customer-orders/filter/" + a.customerOrderCode, null));
    p.add(new Probe("GET", v + "/purchase-orders/filter/{code}", v + "/purchase-orders/filter/" + a.purchaseOrderCode, null));
    p.add(new Probe("GET", v + "/stock-transfers/filter/{code}", v + "/stock-transfers/filter/" + a.stockTransferCode, null));

    // --- ecritures par id
    p.add(new Probe("PUT", v + "/articles/update/{id}", v + "/articles/update/" + a.articleId,
        "{\"codeArticle\":\"" + uniq("X") + "\",\"designation\":\"X\",\"prixUnitaireHt\":1,\"tauxTva\":1,"
            + "\"prixUnitaireTtc\":1,\"category\":{\"id\":" + b.categoryId + "}}"));
    p.add(new Probe("PUT", v + "/categories/update/{id}", v + "/categories/update/" + a.categoryId,
        "{\"code\":\"" + uniq("X") + "\",\"designation\":\"X\"}"));
    p.add(new Probe("PUT", v + "/cities/update/{id}", v + "/cities/update/" + a.cityId,
        "{\"name\":\"X\"}"));
    p.add(new Probe("PUT", v + "/warehouses/update/{id}", v + "/warehouses/update/" + a.warehouseId,
        "{\"code\":\"" + uniq("X") + "\",\"name\":\"X\",\"active\":true}"));
    p.add(new Probe("PUT", v + "/customers/update/{id}", v + "/customers/update/" + a.customerId,
        "{\"nom\":\"X\",\"prenom\":\"P\",\"mail\":\"" + uniq("x") + "@test.local\",\"numTel\":\"+237600000009\","
            + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    p.add(new Probe("PUT", v + "/suppliers/update/{id}", v + "/suppliers/update/" + a.supplierId,
        "{\"nom\":\"X\",\"prenom\":\"P\",\"mail\":\"" + uniq("x") + "@test.local\",\"numTel\":\"+237600000009\","
            + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    p.add(new Probe("POST", v + "/stocks/article/{idArticle}/site/{idSite}/entree",
        v + "/stocks/article/" + a.articleId + "/site/" + a.siteId + "/entree?quantite=1", null));
    p.add(new Probe("POST", v + "/stocks/article/{idArticle}/site/{idSite}/sortie",
        v + "/stocks/article/" + a.articleId + "/site/" + a.siteId + "/sortie?quantite=1", null));
    p.add(new Probe("POST", v + "/stocks/article/{idArticle}/site/{idSite}/correction",
        v + "/stocks/article/" + a.articleId + "/site/" + a.siteId + "/correction?delta=1", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/valider", v + "/customer-orders/" + a.customerOrderId + "/valider", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/reserver", v + "/customer-orders/" + a.customerOrderId + "/reserver", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/preparer", v + "/customer-orders/" + a.customerOrderId + "/preparer", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/expedier", v + "/customer-orders/" + a.customerOrderId + "/expedier", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/livrer", v + "/customer-orders/" + a.customerOrderId + "/livrer", null));
    p.add(new Probe("POST", v + "/customer-orders/{idCommande}/annuler", v + "/customer-orders/" + a.customerOrderId + "/annuler", null));
    p.add(new Probe("POST", v + "/purchase-orders/{idCommande}/valider", v + "/purchase-orders/" + a.purchaseOrderId + "/valider", null));
    p.add(new Probe("POST", v + "/purchase-orders/{idCommande}/annuler", v + "/purchase-orders/" + a.purchaseOrderId + "/annuler", null));
    p.add(new Probe("POST", v + "/purchase-orders/{idCommande}/lignes/{idLigne}/receptionner",
        v + "/purchase-orders/" + a.purchaseOrderId + "/lignes/1/receptionner?quantite=1", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/demander", v + "/stock-transfers/" + a.stockTransferId + "/demander", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/approuver", v + "/stock-transfers/" + a.stockTransferId + "/approuver", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/preparer", v + "/stock-transfers/" + a.stockTransferId + "/preparer", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/expedier", v + "/stock-transfers/" + a.stockTransferId + "/expedier", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/receptionner", v + "/stock-transfers/" + a.stockTransferId + "/receptionner", null));
    p.add(new Probe("POST", v + "/stock-transfers/{idTransfert}/annuler", v + "/stock-transfers/" + a.stockTransferId + "/annuler", null));
    p.add(new Probe("POST", v + "/articles/{idArticle}/photo", v + "/articles/" + a.articleId + "/photo",
        "{\"url\":\"https://example.test/x.png\"}"));
    p.add(new Probe("POST", v + "/clients/{idClient}/photo", v + "/clients/" + a.customerId + "/photo",
        "{\"url\":\"https://example.test/x.png\"}"));
    p.add(new Probe("POST", v + "/fournisseurs/{idFournisseur}/photo", v + "/fournisseurs/" + a.supplierId + "/photo",
        "{\"url\":\"https://example.test/x.png\"}"));
    p.add(new Probe("POST", v + "/tenants/{id}/photo", v + "/tenants/" + a.tenantId + "/photo",
        "{\"url\":\"https://example.test/x.png\"}"));
    // --- suppressions (en dernier : ce sont les plus destructrices)
    p.add(new Probe("DELETE", v + "/articles/delete/{idArticle}", v + "/articles/delete/" + a.articleId, null));
    p.add(new Probe("DELETE", v + "/categories/delete/{idCategory}", v + "/categories/delete/" + a.categoryId, null));
    p.add(new Probe("DELETE", v + "/cities/delete/{idCity}", v + "/cities/delete/" + a.cityId, null));
    p.add(new Probe("DELETE", v + "/sites/delete/{idSite}", v + "/sites/delete/" + a.siteId, null));
    p.add(new Probe("DELETE", v + "/warehouses/delete/{idWarehouse}", v + "/warehouses/delete/" + a.warehouseId, null));
    p.add(new Probe("DELETE", v + "/customers/delete/{id}", v + "/customers/delete/" + a.customerId, null));
    p.add(new Probe("DELETE", v + "/suppliers/delete/{id}", v + "/suppliers/delete/" + a.supplierId, null));
    p.add(new Probe("DELETE", v + "/users/delete/{id}", v + "/users/delete/" + a.userId, null));
    p.add(new Probe("DELETE", v + "/organizations/delete/{idOrganization}", v + "/organizations/delete/" + a.orgId, null));
    p.add(new Probe("DELETE", v + "/tenants/delete/{id}", v + "/tenants/delete/" + a.tenantId, null));
    p.add(new Probe("DELETE", v + "/user-role-assignments/delete/{idAssignment}", v + "/user-role-assignments/delete/" + a.assignmentId, null));
    return p;
  }

  @Test
  public void noEndpointLetsOneTenantTouchAnotherTenantResources() throws Exception {
    Tenant a = provision();
    Tenant b = provision();

    List<String> leaks = new ArrayList<>();
    for (Probe probe : probes(a, b)) {
      var req = switch (probe.method()) {
        case "GET" -> get(probe.url());
        case "PUT" -> put(probe.url());
        case "DELETE" -> delete(probe.url());
        default -> post(probe.url());
      };
      req.header("Authorization", "Bearer " + b.token);
      if (probe.json() != null) {
        req.contentType(MediaType.APPLICATION_JSON).content(probe.json());
      }
      var response = mockMvc.perform(req).andReturn().getResponse();
      int status = response.getStatus();
      if (probe.mode() == Mode.DENY) {
        if (status < 400) {
          leaks.add(probe.method() + " " + probe.pattern() + " -> HTTP " + status + " (attendu 4xx)");
        }
      } else if (probe.mode() == Mode.NO_FOREIGN_DATA) {
        String payload = response.getContentAsString().replaceAll("\\s", "");
        if (status < 400 && payload.contains(probe.json())) {
          leaks.add(probe.method() + " " + probe.pattern() + " -> HTTP " + status
              + " expose une ressource d'un autre tenant (" + probe.json() + ")");
        }
      } else {
        String payload = response.getContentAsString().replaceAll("\\s", "");
        boolean harmless = status >= 400 || payload.isEmpty() || "[]".equals(payload);
        if (!harmless) {
          leaks.add(probe.method() + " " + probe.pattern() + " -> HTTP " + status
              + " avec des donnees d'un autre tenant : " + payload);
        }
      }
    }

    // Les ressources de A doivent toutes etre encore lisibles par A : aucune suppression ni
    // modification n'a abouti.
    for (String url : List.of(
        "/gestiondestock/v1/articles/" + a.articleId,
        "/gestiondestock/v1/categories/" + a.categoryId,
        "/gestiondestock/v1/cities/" + a.cityId,
        "/gestiondestock/v1/sites/" + a.siteId,
        "/gestiondestock/v1/warehouses/" + a.warehouseId,
        "/gestiondestock/v1/customers/" + a.customerId,
        "/gestiondestock/v1/suppliers/" + a.supplierId,
        "/gestiondestock/v1/sales/" + a.saleId,
        "/gestiondestock/v1/customer-orders/" + a.customerOrderId,
        "/gestiondestock/v1/purchase-orders/" + a.purchaseOrderId,
        "/gestiondestock/v1/stock-transfers/" + a.stockTransferId)) {
      int status = mockMvc.perform(get(url).header("Authorization", "Bearer " + a.token))
          .andReturn().getResponse().getStatus();
      if (status != 200) {
        leaks.add("EFFET DE BORD : " + url + " n'est plus lisible par son proprietaire (HTTP " + status + ")");
      }
    }

    assertTrue("Fuites inter-tenant detectees :\n  - " + String.join("\n  - ", leaks), leaks.isEmpty());
  }

  @Test
  public void everyEndpointIsEitherProbedOrJustifiablyExcluded() throws Exception {
    Set<String> exposed = new TreeSet<>();
    handlerMapping.getHandlerMethods().forEach((info, handler) -> {
      if (info.getPathPatternsCondition() == null) {
        return;
      }
      info.getPathPatternsCondition().getPatternValues().stream()
          .filter(path -> path.startsWith("/gestiondestock/"))
          .forEach(exposed::add);
    });

    Set<String> probed = new TreeSet<>();
    Tenant dummy = new Tenant();
    for (Probe probe : probes(dummy, dummy)) {
      probed.add(probe.pattern());
    }

    List<String> unclassified = new ArrayList<>();
    for (String path : exposed) {
      if (!probed.contains(path) && !EXCLUDED.containsKey(path)) {
        unclassified.add(path);
      }
    }

    assertTrue("Endpoints ni sondes par la matrice ni exclus avec justification :\n  - "
        + String.join("\n  - ", unclassified)
        + "\nAjoutez une sonde dans probes(), ou une entree justifiee dans EXCLUDED.",
        unclassified.isEmpty());
  }
}
