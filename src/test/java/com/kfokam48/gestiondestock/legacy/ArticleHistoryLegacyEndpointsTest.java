package com.kfokam48.gestiondestock.legacy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
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
 * Phase 3a : non-regression pour le deplacement des endpoints
 * {@code /articles/historique/{vente,commandeclient,commandefournisseur}/{id}} depuis
 * catalog.ArticleController vers sales/purchasing.*.presentation.rest.legacy (voir
 * ArticleServiceImpl et docs/phase-3a-report.md pour le pourquoi). Verifie que l'URL HTTP produit
 * toujours le meme resultat qu'avant le deplacement (fusion legacy + module neuf), et que le
 * garde-fou de suppression d'un article utilise (desormais base sur la contrainte FK plutot qu'un
 * appel direct aux facades sales/purchasing) fonctionne toujours.
 *
 * <p>Phase 4d : les routes de seed utilisees ici (anciennement {@code /clients/create},
 * {@code /fournisseurs/create}, {@code /ventes/create}, {@code /commandesclients/create},
 * {@code /commandesfournisseurs/create}) ont ete supprimees (voir docs/phase-4d-report.md) et
 * remplacees par leurs equivalents module-neuf ({@code /customers/create}, {@code /suppliers/create},
 * {@code /sales/create}, {@code /customer-orders/create}, {@code /purchase-orders/create}). Le flux
 * "new module" de chaque test merge-historique est donc seede par ces nouvelles routes ; le flux
 * "legacy" (LigneVenteRepository/LigneCommandeClientRepository/LigneCommandeFournisseurRepository)
 * n'a plus aucun chemin d'ecriture HTTP depuis Phase 18-22 deja (ces adaptateurs delegaient deja a
 * sales/purchasing avant meme cet increment) : il reste en base uniquement pour les lignes
 * historiques anterieures a cette migration, jamais alimente par les tests d'integration.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class ArticleHistoryLegacyEndpointsTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private com.kfokam48.gestiondestock.repository.EntrepriseRepository entrepriseRepository;

  @Autowired
  private com.kfokam48.gestiondestock.organization.application.OrganizationService organizationService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static final class Tenant {
    final String token;
    final Long entrepriseId;

    Tenant(String token, Long entrepriseId) {
      this.token = token;
      this.entrepriseId = entrepriseId;
    }
  }

  private String adminToken() throws Exception {
    return adminTenant().token;
  }

  private Tenant adminTenant() throws Exception {
    String email = uniqueCode("art-hist-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Article History Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000050", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk())
        .andReturn();
    Long entrepriseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    return new Tenant(token, entrepriseId);
  }

  private Long defaultSiteId(Tenant tenant) {
    Long organizationId = entrepriseRepository.findById(tenant.entrepriseId).orElseThrow().getOrganizationId();
    return organizationService.ensureDefaultSite(organizationId).getId();
  }

  private void seedStock(Tenant tenant, Long articleId, int quantite) throws Exception {
    Long siteId = defaultSiteId(tenant);
    mockMvc.perform(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId
            + "/entree?quantite=" + quantite + "&reference=seed-test")
            .header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isOk());
  }

  private Long createArticle(String token) throws Exception {
    String catCode = uniqueCode("CAT");
    mockMvc.perform(post("/gestiondestock/v1/categories/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + catCode + "\",\"designation\":\"Cat test\"}"))
        .andExpect(status().isOk());
    JsonNode cat = objectMapper.readTree(mockMvc.perform(get("/gestiondestock/v1/categories/filter/" + catCode)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    long catId = cat.get("id").asLong();

    String artCode = uniqueCode("ART");
    MvcResult articleResult = mockMvc.perform(post("/gestiondestock/v1/articles/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"codeArticle\":\"" + artCode + "\",\"designation\":\"Article test\",\"prixUnitaireHt\":100,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + catId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(articleResult.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createFournisseur(String token) throws Exception {
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/suppliers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"F\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("fourn") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createClient(String token) throws Exception {
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/customers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"C\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("cli") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void findHistoriqueCommandeClientMergesLegacyAndNewModuleLines() throws Exception {
    Tenant tenant = adminTenant();
    long articleId = createArticle(tenant.token);
    long clientId = createClient(tenant.token);
    long siteId = defaultSiteId(tenant);

    mockMvc.perform(post("/gestiondestock/v1/customer-orders/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"order\":{\"code\":\"" + uniqueCode("CMD") + "\",\"customerId\":" + clientId
                + ",\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":4,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/articles/historique/commandeclient/" + articleId)
            .header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertEquals(1, lines.size());
    org.junit.Assert.assertEquals(articleId, lines.get(0).get("article").get("id").asLong());
    org.junit.Assert.assertEquals(4, lines.get(0).get("quantite").asInt());
  }

  @Test
  public void findHistoriqueVentesMergesLegacyAndNewModuleLines() throws Exception {
    Tenant tenant = adminTenant();
    Long articleId = createArticle(tenant.token);
    seedStock(tenant, articleId, 10);
    long siteId = defaultSiteId(tenant);

    mockMvc.perform(post("/gestiondestock/v1/sales/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sale\":{\"code\":\"" + uniqueCode("VEN") + "\",\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":2,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/articles/historique/vente/" + articleId)
            .header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertEquals(1, lines.size());
    org.junit.Assert.assertEquals(articleId.longValue(), lines.get(0).get("article").get("id").asLong());
    org.junit.Assert.assertEquals(2, lines.get(0).get("quantite").asInt());
  }

  @Test
  public void findHistoriqueCommandeFournisseurMergesLegacyAndNewModuleLines() throws Exception {
    Tenant tenant = adminTenant();
    long articleId = createArticle(tenant.token);
    long fournisseurId = createFournisseur(tenant.token);
    long siteId = defaultSiteId(tenant);

    mockMvc.perform(post("/gestiondestock/v1/purchase-orders/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"order\":{\"code\":\"" + uniqueCode("CMF") + "\",\"supplierId\":" + fournisseurId
                + ",\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantiteCommandee\":7,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/articles/historique/commandefournisseur/" + articleId)
            .header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertEquals(1, lines.size());
    org.junit.Assert.assertEquals(articleId, lines.get(0).get("article").get("id").asLong());
    org.junit.Assert.assertEquals(7, lines.get(0).get("quantite").asInt());
  }

  @Test
  public void deleteArticleUsedInCommandeFournisseurIsRejected() throws Exception {
    // Le garde-fou repose desormais sur la contrainte FK (catch DataIntegrityViolationException)
    // plutot que sur un appel direct depuis catalog vers purchasing (Phase 3a) - et, depuis Phase
    // 4d, sur cette meme contrainte FK au lieu du check explicite LigneCommandeFournisseurRepository
    // puisque la ligne est desormais creee dans purchase_order_line (module neuf), pas dans
    // lignecommandefournisseur (legacy, plus jamais alimente depuis Phase 18-22).
    Tenant tenant = adminTenant();
    long articleId = createArticle(tenant.token);
    long fournisseurId = createFournisseur(tenant.token);
    long siteId = defaultSiteId(tenant);

    mockMvc.perform(post("/gestiondestock/v1/purchase-orders/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"order\":{\"code\":\"" + uniqueCode("CMF") + "\",\"supplierId\":" + fournisseurId
                + ",\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantiteCommandee\":3,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/articles/delete/" + articleId).header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ARTICLE_ALREADY_IN_USE"));
  }

  @Test
  public void deleteUnusedArticleStillSucceeds() throws Exception {
    String token = adminToken();
    long articleId = createArticle(token);

    mockMvc.perform(delete("/gestiondestock/v1/articles/delete/" + articleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/articles/" + articleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
