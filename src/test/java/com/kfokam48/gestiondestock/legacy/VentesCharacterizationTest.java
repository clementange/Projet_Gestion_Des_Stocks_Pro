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
 * Phase 0 (filet de securite) : caracterisation du contrat HTTP legacy {@code /ventes/*}, qui
 * delegue depuis Phase 19 a {@code sales.Sale}. Complete
 * {@link LegacyControllersIntegrationTest#ventesCreateFindByCodeRoundTripPreservesCodeAndDate()}
 * (create + filter/code, inchange).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class VentesCharacterizationTest extends AbstractIntegrationTest {

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

  private Tenant adminTenant() throws Exception {
    String email = uniqueCode("ventes-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Ventes Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000042", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk())
        .andReturn();
    Long entrepriseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}";
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    return new Tenant(token, entrepriseId);
  }

  private Long createArticleForTests(String token) throws Exception {
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

  private void seedStock(Tenant tenant, Long articleId, int quantite) throws Exception {
    Long organizationId = entrepriseRepository.findById(tenant.entrepriseId).orElseThrow().getOrganizationId();
    Long siteId = organizationService.ensureDefaultSite(organizationId).getId();
    mockMvc.perform(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId
            + "/entree?quantite=" + quantite + "&reference=seed-test")
            .header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isOk());
  }

  @Test
  public void findByIdUnknownReturns404SaleNotFound() throws Exception {
    Tenant tenant = adminTenant();

    mockMvc.perform(get("/gestiondestock/v1/ventes/999999999").header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));
  }

  @Test
  public void deleteAlwaysReturns400VenteDeleteNotSupported() throws Exception {
    // Comportement volontairement change vs legacy historique (qui supprimait vraiment) : le
    // stock d'une vente a deja ete sorti, la suppression n'a plus de sens metier.
    Tenant tenant = adminTenant();
    Long articleId = createArticleForTests(tenant.token);
    seedStock(tenant, articleId, 10);

    String code = uniqueCode("VEN");
    String payload = "{\"code\":\"" + code + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
        + "\"ligneVentes\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(delete("/gestiondestock/v1/ventes/delete/" + id).header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VENTE_DELETE_NOT_SUPPORTED"));
  }

  @Test
  public void createExceedingAvailableStockIsRejectedAndPersistsNothing() throws Exception {
    Tenant tenant = adminTenant();
    Long articleId = createArticleForTests(tenant.token);
    seedStock(tenant, articleId, 5);

    String code = uniqueCode("VEN");
    String payload = "{\"code\":\"" + code + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
        + "\"ligneVentes\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":999,\"prixUnitaire\":10}]}";

    mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STOCK_INSUFFICIENT"));

    mockMvc.perform(get("/gestiondestock/v1/ventes/filter/" + code).header("Authorization", "Bearer " + tenant.token))
        .andExpect(status().isNotFound());
  }

  @Test
  public void createWithZeroLinesLeaksSaleNotValidCodeFromInnerModule() throws Exception {
    // VentesServiceImpl.save ne catche pas l'InvalidEntityException levee par SaleValidator : le
    // code SALE_NOT_VALID (module sales) traverse tel quel le contrat legacy, jamais remappe en
    // VENTE_NOT_VALID.
    Tenant tenant = adminTenant();
    String payload = "{\"code\":\"" + uniqueCode("VEN") + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
        + "\"ligneVentes\":[]}";

    mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SALE_NOT_VALID"))
        .andExpect(jsonPath("$.errors[0]").value("Une vente doit comporter au moins une ligne"));
  }

  @Test
  public void createWithDuplicateCodeSurfacesAsRaw500WithoutErrorCode() throws Exception {
    // Aucune verification d'unicite applicative sur Sale.code (contrainte UNIQUE uniquement en
    // base) : un code duplique remonte en 500 brut via le handler generique, sans champ "code".
    Tenant tenant = adminTenant();
    Long articleId = createArticleForTests(tenant.token);
    seedStock(tenant, articleId, 100);

    String code = uniqueCode("VEN");
    String payload = "{\"code\":\"" + code + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
        + "\"ligneVentes\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";

    mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenant.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  public void findAllReturnsRawArrayAcrossAllTenantsWithoutPagination() throws Exception {
    Tenant tenantA = adminTenant();
    Tenant tenantB = adminTenant();
    Long articleA = createArticleForTests(tenantA.token);
    Long articleB = createArticleForTests(tenantB.token);
    seedStock(tenantA, articleA, 10);
    seedStock(tenantB, articleB, 10);

    String codeA = uniqueCode("VEN");
    MvcResult saleA = mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenantA.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + codeA + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
                + "\"ligneVentes\":[{\"article\":{\"id\":" + articleA + "},\"quantite\":1,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk())
        .andReturn();
    long idA = objectMapper.readTree(saleA.getResponse().getContentAsString()).get("id").asLong();

    String codeB = uniqueCode("VEN");
    MvcResult saleB = mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + tenantB.token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + codeB + "\",\"dateVente\":\"2026-09-11T00:00:00Z\","
                + "\"ligneVentes\":[{\"article\":{\"id\":" + articleB + "},\"quantite\":1,\"prixUnitaire\":10}]}"))
        .andExpect(status().isOk())
        .andReturn();
    long idB = objectMapper.readTree(saleB.getResponse().getContentAsString()).get("id").asLong();

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/ventes/all").header("Authorization", "Bearer " + tenantA.token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode all = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertTrue(all.isArray());
    boolean containsA = false;
    boolean containsB = false;
    for (JsonNode node : all) {
      if (node.get("id").asLong() == idA) {
        containsA = true;
      }
      if (node.get("id").asLong() == idB) {
        containsB = true;
      }
    }
    org.junit.Assert.assertTrue(containsA);
    org.junit.Assert.assertTrue("vente d'un autre tenant visible : fuite cross-tenant figee", containsB);
  }
}
