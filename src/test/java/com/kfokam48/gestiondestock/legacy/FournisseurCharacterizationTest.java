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
 * Phase 0 (filet de securite) : caracterisation du contrat HTTP legacy {@code /fournisseurs/*},
 * qui delegue depuis Phase 18 a {@code purchasing.Supplier} (voir FournisseurServiceImpl).
 * Complete {@link LegacyControllersIntegrationTest#fournisseurCrudFlowWorksOverHttp()}
 * (create/get happy path, inchange) : contrairement a Client, le DELETE n'etait couvert par
 * AUCUN test avant Phase 0.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class FournisseurCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("fourn-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Fournisseur Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000041", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
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

  private long createFournisseur(String token, String mail) throws Exception {
    String payload = "{\"nom\":\"Fournisseur HTTP\",\"prenom\":\"Test\",\"mail\":\"" + mail + "\","
        + "\"numTel\":\"600000031\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/fournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void deleteThenGetReturns404_previouslyUntestedOverHttp() throws Exception {
    String token = adminToken();
    long id = createFournisseur(token, uniqueCode("fourn") + "@test.local");

    mockMvc.perform(delete("/gestiondestock/v1/fournisseurs/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/fournisseurs/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
  }

  @Test
  public void createWithMissingNomReturnsCleanValidationError() throws Exception {
    String token = adminToken();
    String payload = "{\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("fourn") + "@test.local\","
        + "\"numTel\":\"600000031\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";

    mockMvc.perform(post("/gestiondestock/v1/fournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FOURNISSEUR_NOT_VALID"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0]").value("Veuillez renseigner le nom du fournisseur"));
  }

  @Test
  public void findAllReturnsRawArrayAcrossAllTenantsWithoutPagination() throws Exception {
    String tokenA = adminToken();
    String tokenB = adminToken();
    long fournA = createFournisseur(tokenA, uniqueCode("fournA") + "@test.local");
    long fournB = createFournisseur(tokenB, uniqueCode("fournB") + "@test.local");

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/fournisseurs/all").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode all = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertTrue(all.isArray());
    boolean containsA = false;
    boolean containsB = false;
    for (JsonNode node : all) {
      if (node.get("id").asLong() == fournA) {
        containsA = true;
      }
      if (node.get("id").asLong() == fournB) {
        containsB = true;
      }
    }
    org.junit.Assert.assertTrue(containsA);
    org.junit.Assert.assertTrue("fournisseur d'un autre tenant visible : fuite cross-tenant figee", containsB);
  }

  @Test
  public void deleteFournisseurReferencedByCommandeFournisseurIsRejected() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);
    long fournisseurId = createFournisseur(token, uniqueCode("fourn") + "@test.local");

    String payload = "{\"code\":\"" + uniqueCode("CMF") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"etatCommande\":\"EN_PREPARATION\",\"fournisseur\":{\"id\":" + fournisseurId + "},"
        + "\"ligneCommandeFournisseurs\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    mockMvc.perform(post("/gestiondestock/v1/commandesfournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/fournisseurs/delete/" + fournisseurId).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPLIER_ALREADY_IN_USE"));
  }

  @Test
  public void twoFournisseursWithSameMailBothSucceedBecauseNoUniquenessCheck() throws Exception {
    String token = adminToken();
    String mail = uniqueCode("dup") + "@test.local";

    long first = createFournisseur(token, mail);
    long second = createFournisseur(token, mail);

    org.junit.Assert.assertNotEquals(first, second);
  }
}
