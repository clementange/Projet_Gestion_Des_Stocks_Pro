package com.kfokam48.gestiondestock.legacy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
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
 * Phase 0 (filet de securite) : caracterisation du contrat HTTP legacy {@code
 * /commandesclients/*}, qui delegue depuis Phase 20 a {@code sales.CustomerOrder}. Complete
 * {@link LegacyControllersIntegrationTest} (regressions findById/etatCommande manquant,
 * update/etat -> VALIDEE, inchanges).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CommandeClientCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("cc-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe CC Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000043", null,
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

  private long createClient(String token) throws Exception {
    MvcResult clientResult = mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"C\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("cli") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(clientResult.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createCommandeClient(String token, long clientId, long articleId, String code) throws Exception {
    String payload = "{\"code\":\"" + code + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"client\":{\"id\":" + clientId + "},\"ligneCommandeClients\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void findByIdAndFindByCodeUnknownReturn404WithCustomerOrderNotFoundCode() throws Exception {
    // Incoherence figee : le 404 porte le code du module neuf (CUSTOMER_ORDER_NOT_FOUND), jamais
    // COMMANDE_CLIENT_NOT_FOUND (contrat legacy).
    String token = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/commandesclients/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CUSTOMER_ORDER_NOT_FOUND"));

    mockMvc.perform(get("/gestiondestock/v1/commandesclients/filter/INCONNU-XYZ").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CUSTOMER_ORDER_NOT_FOUND"));
  }

  @Test
  public void deleteAlwaysReturns400CommandeClientAlreadyInUse() throws Exception {
    String token = adminToken();
    long clientId = createClient(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeClient(token, clientId, articleId, uniqueCode("CMD"));

    mockMvc.perform(delete("/gestiondestock/v1/commandesclients/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_ALREADY_IN_USE"));
  }

  @Test
  public void fineGrainedMutationEndpointsAlwaysReturn400EvenWithUnknownIds() throws Exception {
    // Aucun lookup prealable : les 4 endpoints de mutation fine repondent 400 avant meme de
    // verifier que la commande/ligne/article existe.
    String token = adminToken();

    mockMvc.perform(patch("/gestiondestock/v1/commandesclients/update/quantite/999999999/1/5")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_MUTATION_UNSUPPORTED"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesclients/update/client/999999999/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_MUTATION_UNSUPPORTED"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesclients/update/article/999999999/1/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_MUTATION_UNSUPPORTED"));

    mockMvc.perform(delete("/gestiondestock/v1/commandesclients/delete/article/999999999/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_MUTATION_UNSUPPORTED"));
  }

  @Test
  public void updateEtatDirectlyToLivreeFromBrouillonIsRejected() throws Exception {
    // Figige l'inatteignabilite de LIVREE par le contrat legacy seul : aucun endpoint
    // reserver/preparer/expedier n'est expose sous /commandesclients.
    String token = adminToken();
    long clientId = createClient(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeClient(token, clientId, articleId, uniqueCode("CMD"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesclients/update/etat/" + id + "/LIVREE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_TRANSITION_UNSUPPORTED"));
  }

  @Test
  public void findAllReturnsRawArrayAcrossAllTenantsWithoutPagination() throws Exception {
    String tokenA = adminToken();
    String tokenB = adminToken();
    long clientA = createClient(tokenA);
    long clientB = createClient(tokenB);
    Long articleA = createArticleForTests(tokenA);
    Long articleB = createArticleForTests(tokenB);
    long idA = createCommandeClient(tokenA, clientA, articleA, uniqueCode("CMD"));
    long idB = createCommandeClient(tokenB, clientB, articleB, uniqueCode("CMD"));

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/commandesclients/all").header("Authorization", "Bearer " + tokenA))
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
    org.junit.Assert.assertTrue("commande d'un autre tenant visible : fuite cross-tenant figee", containsB);
  }

  @Test
  public void findAllLignesCommandeDropsBackReferenceAndIdEntreprise() throws Exception {
    // CommandeClientServiceImpl.findAllLignesCommandesClientByCommandeClientId reconstruit des
    // LigneCommandeClientDto sans jamais renseigner commandeClient ni idEntreprise : perte
    // silencieuse figee comme comportement actuel du contrat JSON.
    String token = adminToken();
    long clientId = createClient(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeClient(token, clientId, articleId, uniqueCode("CMD"));

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/commandesclients/lignesCommande/" + id)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertEquals(1, lines.size());
    JsonNode line = lines.get(0);
    org.junit.Assert.assertTrue("commandeClient doit etre absent/null sur chaque ligne renvoyee",
        line.get("commandeClient") == null || line.get("commandeClient").isNull());
    org.junit.Assert.assertTrue("idEntreprise doit etre absent/null sur chaque ligne renvoyee",
        line.get("idEntreprise") == null || line.get("idEntreprise").isNull());
  }

  @Test
  public void createWithUnknownClientIdReturns404ClientNotFound() throws Exception {
    String token = adminToken();
    String payload = "{\"code\":\"" + uniqueCode("CMD") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"etatCommande\":\"EN_PREPARATION\",\"client\":{\"id\":999999999}}";

    mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
  }

  @Test
  public void createWithDuplicateCodeSurfacesAsRaw500WithoutErrorCode() throws Exception {
    String token = adminToken();
    long clientId = createClient(token);
    Long articleId = createArticleForTests(token);
    String code = uniqueCode("CMD");

    createCommandeClient(token, clientId, articleId, code);

    String payload = "{\"code\":\"" + code + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"client\":{\"id\":" + clientId + "},\"ligneCommandeClients\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").doesNotExist());
  }
}
