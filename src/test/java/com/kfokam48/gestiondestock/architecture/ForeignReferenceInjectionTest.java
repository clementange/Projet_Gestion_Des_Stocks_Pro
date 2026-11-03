package com.kfokam48.gestiondestock.architecture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 5e : l'appartenance des REFERENCES fournies par le client, et non plus seulement celle de
 * la ressource ciblee.
 *
 * <p>C'est l'angle mort que {@code CrossTenantEndpointMatrixTest} laisse ouvert par construction :
 * elle sonde l'{@code id} de la ressource visee, jamais la valeur d'un CHAMP du corps. Un appelant
 * du tenant B passe donc ici des references appartenant au tenant A — une categorie, un client, un
 * fournisseur, l'article d'une ligne — sur des endpoints ou il a par ailleurs toutes les
 * permissions et un site bien a lui. La Phase 5d a fermé le premier cas de cette classe
 * ({@code UserDto.idEntreprise}) ; ce test couvre les suivants.
 *
 * <p>Attendu pour chaque sonde : **404 avec le {@code *_NOT_FOUND} de l'entite REFERENCEE**. C'est
 * la convention d'appartenance du depot (un refus d'appartenance se masque en « inexistant »), et
 * elle est ici doublement juste : pour l'appelant, cette ressource n'existe effectivement pas.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ForeignReferenceInjectionTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Test-Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private Fixture a;
  private Fixture b;

  private static String uniq(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Jeu complet de ressources d'un tenant : de quoi construire n'importe quel agregat. */
  private static final class Fixture {
    String token;
    long orgId;
    long cityId;
    long siteId;
    long otherSiteId;
    long categoryId;
    long articleId;
    long customerId;
    long supplierId;
  }

  /** Une tentative d'injection : quelle reference d'autrui, sur quel endpoint, refusee comment. */
  private record Probe(String label, MockHttpServletRequestBuilder request, String expectedCode) {}

  @BeforeEach
  public void provisionTwoFullyEquippedTenants() throws Exception {
    a = tenant("ref-A");
    b = tenant("ref-B");
  }

  private Fixture tenant(String label) throws Exception {
    Fixture f = new Fixture();
    String email = uniq(label) + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe " + label, "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000101", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"), PASSWORD, PASSWORD);
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    String login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    f.token = objectMapper.readTree(login).get("accessToken").asText();
    f.orgId = objectMapper.readTree(Base64.getUrlDecoder().decode(f.token.split("\\.")[1]))
        .get("organizationId").asLong();

    f.cityId = idOf(ok(post("/gestiondestock/v1/cities/create")
        .content("{\"name\":\"Douala\",\"organization\":{\"id\":" + f.orgId + "}}"), f.token));
    f.siteId = idOf(ok(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + f.cityId + "}}"), f.token));
    f.otherSiteId = idOf(ok(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S2") + "\",\"name\":\"Entrepot 2\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + f.cityId + "}}"), f.token));
    f.categoryId = idOf(ok(post("/gestiondestock/v1/categories/create")
        .content("{\"code\":\"" + uniq("C") + "\",\"designation\":\"Cat\"}"), f.token));
    f.articleId = idOf(ok(post("/gestiondestock/v1/articles/create").content(
        "{\"codeArticle\":\"" + uniq("A") + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + f.categoryId
            + "}}"), f.token));
    f.customerId = idOf(ok(post("/gestiondestock/v1/customers/create").content(
        "{\"nom\":\"Client\",\"prenom\":\"Un\",\"email\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000102\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), f.token));
    f.supplierId = idOf(ok(post("/gestiondestock/v1/suppliers/create").content(
        "{\"nom\":\"Fournisseur\",\"prenom\":\"Un\",\"email\":\"" + uniq("four") + "@test.local\","
            + "\"numTel\":\"+237600000103\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), f.token));
    return f;
  }

  /**
   * Toutes les sondes sont executees avant d'echouer, et le rapport les liste toutes : decouvrir
   * les fuites une par une au fil des runs couterait un cycle complet par point d'injection.
   */
  @Test
  public void noEndpointAcceptsAReferenceBelongingToAnotherTenant() throws Exception {
    List<String> leaks = new ArrayList<>();
    for (Probe probe : probes()) {
      try {
        mockMvc.perform(probe.request().header("Authorization", "Bearer " + b.token)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(probe.expectedCode()));
      } catch (AssertionError e) {
        leaks.add("  - " + probe.label() + " : attendu 404/" + probe.expectedCode()
            + " -> " + e.getMessage().replaceAll("\\s+", " ").trim());
      }
    }
    if (!leaks.isEmpty()) {
      throw new AssertionError("Le tenant B a pu utiliser " + leaks.size()
          + " reference(s) du tenant A :\n" + String.join("\n", leaks));
    }
  }

  private List<Probe> probes() {
    List<Probe> p = new ArrayList<>();
    String v = "/gestiondestock/v1";

    // 1-2. Categorie d'autrui sur la creation puis la mise a jour d'un article.
    p.add(new Probe("POST /articles/create (category de A)",
        post(v + "/articles/create").content(
            "{\"codeArticle\":\"" + uniq("HJ") + "\",\"designation\":\"Pirate\",\"prixUnitaireHt\":1,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":1.19,\"category\":{\"id\":" + a.categoryId + "}}"),
        "CATEGORY_NOT_FOUND"));
    p.add(new Probe("PUT /articles/update/{id} (category de A)",
        put(v + "/articles/update/" + b.articleId).content(
            "{\"codeArticle\":\"" + uniq("HJ") + "\",\"designation\":\"Pirate\",\"prixUnitaireHt\":1,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":1.19,\"category\":{\"id\":" + a.categoryId + "}}"),
        "CATEGORY_NOT_FOUND"));

    // 3-4. Client d'autrui, puis article d'autrui dans une ligne, sur la commande client.
    p.add(new Probe("POST /customer-orders/create (customerId de A)",
        post(v + "/customer-orders/create").content(
            "{\"order\":{\"code\":\"" + uniq("CC") + "\",\"customerId\":" + a.customerId + ","
                + "\"site\":{\"id\":" + b.siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"),
        "CUSTOMER_NOT_FOUND"));
    p.add(new Probe("POST /customer-orders/create (article de A dans une ligne)",
        post(v + "/customer-orders/create").content(
            "{\"order\":{\"code\":\"" + uniq("CC") + "\",\"customerId\":" + b.customerId + ","
                + "\"site\":{\"id\":" + b.siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"),
        "ARTICLE_NOT_FOUND"));

    // 5-6. Idem cote fournisseur.
    p.add(new Probe("POST /purchase-orders/create (supplierId de A)",
        post(v + "/purchase-orders/create").content(
            "{\"order\":{\"code\":\"" + uniq("CF") + "\",\"supplierId\":" + a.supplierId + ","
                + "\"site\":{\"id\":" + b.siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantiteCommandee\":1,"
                + "\"prixUnitaire\":10}]}"),
        "SUPPLIER_NOT_FOUND"));
    p.add(new Probe("POST /purchase-orders/create (article de A dans une ligne)",
        post(v + "/purchase-orders/create").content(
            "{\"order\":{\"code\":\"" + uniq("CF") + "\",\"supplierId\":" + b.supplierId + ","
                + "\"site\":{\"id\":" + b.siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantiteCommandee\":1,"
                + "\"prixUnitaire\":10}]}"),
        "ARTICLE_NOT_FOUND"));

    // 7-8. Article d'autrui dans une ligne de vente, puis de transfert.
    p.add(new Probe("POST /sales/create (article de A dans une ligne)",
        post(v + "/sales/create").content(
            "{\"sale\":{\"code\":\"" + uniq("VEN") + "\",\"site\":{\"id\":" + b.siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"),
        "ARTICLE_NOT_FOUND"));
    p.add(new Probe("POST /stock-transfers/create (article de A dans une ligne)",
        post(v + "/stock-transfers/create").content(
            "{\"transfer\":{\"code\":\"" + uniq("TR") + "\",\"originSite\":{\"id\":" + b.siteId + "},"
                + "\"destinationSite\":{\"id\":" + b.otherSiteId + "},\"requestedByUserId\":1},"
                + "\"lines\":[{\"article\":{\"id\":" + a.articleId + "},\"quantite\":1}]}"),
        "ARTICLE_NOT_FOUND"));

    // 9-11. Article d'autrui sur les trois mouvements de stock, vers un site bien a soi.
    // Le parametre differe par action : entree/sortie prennent "quantite", correction prend
    // "delta". Ma premiere version envoyait "quantite" partout, donc la sonde "correction"
    // echouait en 400 (parametre requis absent) AVANT d'atteindre le controleur : elle ne prouvait
    // rien, ni en rouge ni en vert. Corrige - voir docs/phase-5e-report.md.
    for (String action : List.of("entree", "sortie")) {
      p.add(new Probe("POST /stocks/article/{A}/site/{B}/" + action,
          post(v + "/stocks/article/" + a.articleId + "/site/" + b.siteId + "/" + action
              + "?quantite=5&reference=REF"),
          "ARTICLE_NOT_FOUND"));
    }
    p.add(new Probe("POST /stocks/article/{A}/site/{B}/correction",
        post(v + "/stocks/article/" + a.articleId + "/site/" + b.siteId
            + "/correction?delta=5&reference=REF"),
        "ARTICLE_NOT_FOUND"));
    return p;
  }

  /** Contre-epreuve : les memes endpoints marchent avec les references de B. */
  @Test
  public void theSameEndpointsStillWorkWithOwnReferences() throws Exception {
    ok(post("/gestiondestock/v1/articles/create").content(
        "{\"codeArticle\":\"" + uniq("OK") + "\",\"designation\":\"Propre\",\"prixUnitaireHt\":1,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":1.19,\"category\":{\"id\":" + b.categoryId + "}}"),
        b.token);
    ok(post("/gestiondestock/v1/customer-orders/create").content(
        "{\"order\":{\"code\":\"" + uniq("CC") + "\",\"customerId\":" + b.customerId + ","
            + "\"site\":{\"id\":" + b.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}"),
        b.token);
    ok(post("/gestiondestock/v1/purchase-orders/create").content(
        "{\"order\":{\"code\":\"" + uniq("CF") + "\",\"supplierId\":" + b.supplierId + ","
            + "\"site\":{\"id\":" + b.siteId + "}},"
            + "\"lines\":[{\"article\":{\"id\":" + b.articleId + "},\"quantiteCommandee\":1,"
            + "\"prixUnitaire\":10}]}"),
        b.token);
    ok(post("/gestiondestock/v1/stocks/article/" + b.articleId + "/site/" + b.siteId
        + "/entree?quantite=5&reference=REF"), b.token);

    // Et les ressources de A restent intactes et lisibles par A.
    mockMvc.perform(get("/gestiondestock/v1/articles/" + a.articleId)
            .header("Authorization", "Bearer " + a.token))
        .andExpect(status().isOk());
    mockMvc.perform(get("/gestiondestock/v1/categories/" + a.categoryId)
            .header("Authorization", "Bearer " + a.token))
        .andExpect(status().isOk());
  }

  private String ok(MockHttpServletRequestBuilder request, String token) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private long idOf(String json) throws Exception {
    return objectMapper.readTree(json).get("id").asLong();
  }
}
