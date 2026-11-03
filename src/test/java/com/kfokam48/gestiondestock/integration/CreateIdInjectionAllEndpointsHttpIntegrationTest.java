package com.kfokam48.gestiondestock.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 5b-2g : « un create n'est jamais un update » applique aux 10 endpoints de creation restes
 * en dette apres 5b-2f.
 *
 * <p>Pour chacun : le tenant B poste sur l'endpoint de creation en fournissant l'id d'une
 * ressource du tenant A, accompagne de ses propres references valides (ville, site, categorie...).
 * Attendu : 400 {@code CREATION_ID_FORBIDDEN}, et la ressource de A strictement inchangee et
 * toujours lisible par A. Sans la garde, {@code repository.save()} effectue un MERGE JPA : la
 * ressource de A est reecrite et, les controleurs forcant par ailleurs l'organisation depuis le
 * principal, basculee dans l'organisation de B - voir docs/phase-5b2g-report.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class CreateIdInjectionAllEndpointsHttpIntegrationTest extends AbstractIntegrationTest {

  private static final String FORBIDDEN = "CREATION_ID_FORBIDDEN";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniq(String p) {
    return p + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Jeu de ressources d'un tenant, suffisant pour creer n'importe quel agregat. */
  private static final class Fixture {
    String token;
    long orgId;
    long cityId;
    long siteId;
    long otherSiteId;
    long categoryId;
    long articleId;
    // Phase 5e : customerId/supplierId doivent desormais referencer de VRAIES ressources du
    // tenant. Les fixtures utilisaient l'id fictif 1, qui ne designait rien - voir
    // docs/phase-5e-report.md.
    long customerId;
    long supplierId;
  }

  private String postOk(String url, String token, String body) throws Exception {
    return mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  private long idOf(String json) throws Exception {
    return objectMapper.readTree(json).get("id").asLong();
  }

  private Fixture tenant() throws Exception {
    Fixture f = new Fixture();
    String email = uniq("g2-admin") + "@test.local";
    TenantRegistrationRequest reg = new TenantRegistrationRequest(
        "Societe 5b2g", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000080", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)))
        .andExpect(status().isOk());
    MvcResult login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}"))
        .andExpect(status().isOk()).andReturn();
    f.token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
    f.orgId = objectMapper.readTree(java.util.Base64.getUrlDecoder()
        .decode(f.token.split("\\.")[1])).get("organizationId").asLong();

    f.cityId = idOf(postOk("/gestiondestock/v1/cities/create", f.token,
        "{\"name\":\"Douala\",\"organization\":{\"id\":" + f.orgId + "}}"));
    f.siteId = idOf(postOk("/gestiondestock/v1/sites/create", f.token,
        "{\"code\":\"" + uniq("S") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + f.cityId + "}}"));
    f.otherSiteId = idOf(postOk("/gestiondestock/v1/sites/create", f.token,
        "{\"code\":\"" + uniq("S2") + "\",\"name\":\"Entrepot 2\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + f.cityId + "}}"));
    f.categoryId = idOf(postOk("/gestiondestock/v1/categories/create", f.token,
        "{\"code\":\"" + uniq("C") + "\",\"designation\":\"Cat\"}"));
    f.articleId = idOf(postOk("/gestiondestock/v1/articles/create", f.token,
        "{\"codeArticle\":\"" + uniq("A") + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + f.categoryId + "}}"));
    f.customerId = idOf(postOk("/gestiondestock/v1/customers/create", f.token,
        "{\"nom\":\"Client\",\"prenom\":\"Un\",\"email\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000104\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"));
    f.supplierId = idOf(postOk("/gestiondestock/v1/suppliers/create", f.token,
        "{\"nom\":\"Fournisseur\",\"prenom\":\"Un\",\"email\":\"" + uniq("four") + "@test.local\","
            + "\"numTel\":\"+237600000105\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"));
    return f;
  }

  /** Tente la creation avec l'id d'autrui et exige le rejet par la garde centrale. */
  private void expectRejected(String url, String token, String body) throws Exception {
    mockMvc.perform(post(url).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(FORBIDDEN));
  }

  private void expectUnchanged(String url, String token, String jsonPathExpr, Object expected) throws Exception {
    mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath(jsonPathExpr).value(expected));
  }

  // ------------------------------------------------------------------ CRUD simples

  @Test
  public void createArticleRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    expectRejected("/gestiondestock/v1/articles/create", b.token,
        "{\"id\":" + a.articleId + ",\"codeArticle\":\"" + uniq("HJ") + "\",\"designation\":\"Detourne\","
            + "\"prixUnitaireHt\":1,\"tauxTva\":1,\"prixUnitaireTtc\":1,"
            + "\"category\":{\"id\":" + b.categoryId + "}}");
    expectUnchanged("/gestiondestock/v1/articles/" + a.articleId, a.token, "$.designation", "Article");
  }

  @Test
  public void createCategoryRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    expectRejected("/gestiondestock/v1/categories/create", b.token,
        "{\"id\":" + a.categoryId + ",\"code\":\"" + uniq("HJ") + "\",\"designation\":\"Detourne\"}");
    expectUnchanged("/gestiondestock/v1/categories/" + a.categoryId, a.token, "$.designation", "Cat");
  }

  @Test
  public void createCityRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    expectRejected("/gestiondestock/v1/cities/create", b.token,
        "{\"id\":" + a.cityId + ",\"name\":\"Detournee\",\"organization\":{\"id\":" + b.orgId + "}}");
    expectUnchanged("/gestiondestock/v1/cities/" + a.cityId, a.token, "$.name", "Douala");
  }

  @Test
  public void createWarehouseRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    long warehouseA = idOf(postOk("/gestiondestock/v1/warehouses/create", a.token,
        "{\"code\":\"" + uniq("WH") + "\",\"name\":\"WH A\",\"active\":true,"
            + "\"site\":{\"id\":" + a.siteId + "}}"));
    expectRejected("/gestiondestock/v1/warehouses/create", b.token,
        "{\"id\":" + warehouseA + ",\"code\":\"" + uniq("HJ") + "\",\"name\":\"Detourne\",\"active\":true,"
            + "\"site\":{\"id\":" + b.siteId + "}}");
    expectUnchanged("/gestiondestock/v1/warehouses/" + warehouseA, a.token, "$.name", "WH A");
  }

  @Test
  public void createCustomerRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    long customerA = idOf(postOk("/gestiondestock/v1/customers/create", a.token,
        "{\"nom\":\"Client A\",\"prenom\":\"P\",\"mail\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000001\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    expectRejected("/gestiondestock/v1/customers/create", b.token,
        "{\"id\":" + customerA + ",\"nom\":\"Detourne\",\"prenom\":\"P\",\"mail\":\"" + uniq("hj") + "@test.local\","
            + "\"numTel\":\"+237600000002\",\"adresse\":{\"adresse1\":\"y\",\"ville\":\"y\",\"pays\":\"y\",\"codePostale\":\"11111\"}}");
    expectUnchanged("/gestiondestock/v1/customers/" + customerA, a.token, "$.nom", "Client A");
  }

  @Test
  public void createSupplierRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    long supplierA = idOf(postOk("/gestiondestock/v1/suppliers/create", a.token,
        "{\"nom\":\"Fournisseur A\",\"prenom\":\"P\",\"mail\":\"" + uniq("fou") + "@test.local\","
            + "\"numTel\":\"+237600000003\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"));
    expectRejected("/gestiondestock/v1/suppliers/create", b.token,
        "{\"id\":" + supplierA + ",\"nom\":\"Detourne\",\"prenom\":\"P\",\"mail\":\"" + uniq("hj") + "@test.local\","
            + "\"numTel\":\"+237600000004\",\"adresse\":{\"adresse1\":\"y\",\"ville\":\"y\",\"pays\":\"y\",\"codePostale\":\"11111\"}}");
    expectUnchanged("/gestiondestock/v1/suppliers/" + supplierA, a.token, "$.nom", "Fournisseur A");
  }

  // ------------------------------------------------------- agregats a machine d'etats

  @Test
  public void createSaleRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    postOk("/gestiondestock/v1/stocks/article/" + a.articleId + "/site/" + a.siteId
        + "/entree?quantite=50&reference=INIT", a.token, "");
    String codeA = uniq("VEN");
    long saleA = idOf(postOk("/gestiondestock/v1/sales/create", a.token,
        "{\"sale\":{\"code\":\"" + codeA + "\",\"site\":{\"id\":" + a.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"));

    postOk("/gestiondestock/v1/stocks/article/" + b.articleId + "/site/" + b.siteId
        + "/entree?quantite=50&reference=INIT", b.token, "");
    expectRejected("/gestiondestock/v1/sales/create", b.token,
        "{\"sale\":{\"id\":" + saleA + ",\"code\":\"" + uniq("HJ") + "\",\"site\":{\"id\":" + b.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}");
    expectUnchanged("/gestiondestock/v1/sales/" + saleA, a.token, "$.code", codeA);
  }

  @Test
  public void createCustomerOrderRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    String codeA = uniq("CMD");
    long orderA = idOf(postOk("/gestiondestock/v1/customer-orders/create", a.token,
        "{\"order\":{\"code\":\"" + codeA + "\",\"customerId\":" + a.customerId + ",\"site\":{\"id\":" + a.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"));
    expectRejected("/gestiondestock/v1/customer-orders/create", b.token,
        "{\"order\":{\"id\":" + orderA + ",\"code\":\"" + uniq("HJ") + "\",\"customerId\":" + b.customerId + ","
            + "\"site\":{\"id\":" + b.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}");
    expectUnchanged("/gestiondestock/v1/customer-orders/" + orderA, a.token, "$.code", codeA);
  }

  @Test
  public void createPurchaseOrderRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    String codeA = uniq("PO");
    long orderA = idOf(postOk("/gestiondestock/v1/purchase-orders/create", a.token,
        "{\"order\":{\"code\":\"" + codeA + "\",\"supplierId\":" + a.supplierId + ",\"site\":{\"id\":" + a.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantiteCommandee\":1,\"prixUnitaire\":10}]}"));
    expectRejected("/gestiondestock/v1/purchase-orders/create", b.token,
        "{\"order\":{\"id\":" + orderA + ",\"code\":\"" + uniq("HJ") + "\",\"supplierId\":" + b.supplierId + ","
            + "\"site\":{\"id\":" + b.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantiteCommandee\":1,\"prixUnitaire\":10}]}");
    expectUnchanged("/gestiondestock/v1/purchase-orders/" + orderA, a.token, "$.code", codeA);
  }

  @Test
  public void createStockTransferRejectsClientSuppliedId() throws Exception {
    Fixture a = tenant();
    Fixture b = tenant();
    String codeA = uniq("TR");
    long transferA = idOf(postOk("/gestiondestock/v1/stock-transfers/create", a.token,
        "{\"transfer\":{\"code\":\"" + codeA + "\",\"originSite\":{\"id\":" + a.siteId + "},"
            + "\"destinationSite\":{\"id\":" + a.otherSiteId + "},\"requestedByUserId\":1},"
            + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1}]}"));
    expectRejected("/gestiondestock/v1/stock-transfers/create", b.token,
        "{\"transfer\":{\"id\":" + transferA + ",\"code\":\"" + uniq("HJ") + "\","
            + "\"originSite\":{\"id\":" + b.siteId + "},\"destinationSite\":{\"id\":" + b.otherSiteId + "},"
            + "\"requestedByUserId\":1},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantite\":1}]}");
    expectUnchanged("/gestiondestock/v1/stock-transfers/" + transferA, a.token, "$.code", codeA);
  }
}
