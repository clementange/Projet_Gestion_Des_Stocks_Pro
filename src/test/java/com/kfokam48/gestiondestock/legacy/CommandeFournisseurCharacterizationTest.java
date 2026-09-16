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
import java.math.BigDecimal;
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
 * /commandesfournisseurs/*}, qui delegue depuis Phase 21 a {@code purchasing.PurchaseOrder}.
 * Complete {@link LegacyControllersIntegrationTest} (regression validation etatCommande manquant,
 * inchangee). Contrairement a CommandeClient, LIVREE reste atteignable via le contrat legacy seul
 * (pas d'etats intermediaires "reserve/prepare/expedie" cote achats) : c'est le seul des trois
 * flows "commande" entierement pilotable via /commandesfournisseurs/* seul.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CommandeFournisseurCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("cf-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe CF Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000044", null,
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

  private long createFournisseur(String token) throws Exception {
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/fournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"F\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("fourn") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createCommandeFournisseur(String token, long fournisseurId, long articleId, String code, int quantite) throws Exception {
    String payload = "{\"code\":\"" + code + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"fournisseur\":{\"id\":" + fournisseurId + "},\"ligneCommandeFournisseurs\":[{\"article\":{\"id\":" + articleId
        + "},\"quantite\":" + quantite + ",\"prixUnitaire\":10}]}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/commandesfournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  }

  private BigDecimal stockReel(String token, long articleId) throws Exception {
    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/mvtstk/stockreel/" + articleId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return new BigDecimal(result.getResponse().getContentAsString());
  }

  @Test
  public void findByIdAndFindByCodeUnknownReturn404WithPurchaseOrderNotFoundCode() throws Exception {
    String token = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/commandesfournisseurs/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOT_FOUND"));

    mockMvc.perform(get("/gestiondestock/v1/commandesfournisseurs/filter/INCONNU-XYZ").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PURCHASE_ORDER_NOT_FOUND"));
  }

  @Test
  public void deleteAlwaysReturns400CommandeFournisseurAlreadyInUse() throws Exception {
    String token = adminToken();
    long fournisseurId = createFournisseur(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeFournisseur(token, fournisseurId, articleId, uniqueCode("CMF"), 5);

    mockMvc.perform(delete("/gestiondestock/v1/commandesfournisseurs/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_ALREADY_IN_USE"));
  }

  @Test
  public void fineGrainedMutationEndpointsAlwaysReturn400EvenWithUnknownIds() throws Exception {
    String token = adminToken();

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/quantite/999999999/1/5")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_MUTATION_UNSUPPORTED"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/fournisseur/999999999/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_MUTATION_UNSUPPORTED"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/article/999999999/1/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_MUTATION_UNSUPPORTED"));

    mockMvc.perform(delete("/gestiondestock/v1/commandesfournisseurs/delete/article/999999999/1")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_MUTATION_UNSUPPORTED"));
  }

  @Test
  public void fullLegalFlowValideeThenLivreeReceivesStockAndClosesOrder() throws Exception {
    // Seul des trois flows "commande" entierement pilotable via le contrat legacy seul :
    // BROUILLON -> VALIDEE -> LIVREE (reception integrale en un coup, receiveAllLinesInFull).
    String token = adminToken();
    long fournisseurId = createFournisseur(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeFournisseur(token, fournisseurId, articleId, uniqueCode("CMF"), 20);

    BigDecimal stockBefore = stockReel(token, articleId);

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/etat/" + id + "/VALIDEE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.etatCommande").value("VALIDEE"));

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/etat/" + id + "/LIVREE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.etatCommande").value("LIVREE"));

    BigDecimal stockAfter = stockReel(token, articleId);
    org.junit.Assert.assertEquals(0, stockBefore.add(BigDecimal.valueOf(20)).compareTo(stockAfter));
  }

  @Test
  public void updateEtatOnAlreadyReceivedOrderIsRejected() throws Exception {
    String token = adminToken();
    long fournisseurId = createFournisseur(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeFournisseur(token, fournisseurId, articleId, uniqueCode("CMF"), 5);

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/etat/" + id + "/VALIDEE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/etat/" + id + "/LIVREE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(patch("/gestiondestock/v1/commandesfournisseurs/update/etat/" + id + "/VALIDEE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_NON_MODIFIABLE"));
  }

  @Test
  public void findAllReturnsRawArrayAcrossAllTenantsWithoutPagination() throws Exception {
    String tokenA = adminToken();
    String tokenB = adminToken();
    long fournA = createFournisseur(tokenA);
    long fournB = createFournisseur(tokenB);
    Long articleA = createArticleForTests(tokenA);
    Long articleB = createArticleForTests(tokenB);
    long idA = createCommandeFournisseur(tokenA, fournA, articleA, uniqueCode("CMF"), 1);
    long idB = createCommandeFournisseur(tokenB, fournB, articleB, uniqueCode("CMF"), 1);

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/commandesfournisseurs/all").header("Authorization", "Bearer " + tokenA))
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
  public void findAllLignesCommandeDropsReceptionFieldsAndBackReference() throws Exception {
    // LigneCommandeFournisseurDto n'expose que "quantite" (mappee depuis quantiteCommandee) :
    // quantiteRecue et quantiteRestanteARecevoir (module neuf) sont silencieusement absents du
    // contrat JSON legacy. Le champ "commandeFournisseur" (type entite JPA brute, sans
    // @JsonIgnore) reste toujours null en pratique cote adapter.
    String token = adminToken();
    long fournisseurId = createFournisseur(token);
    Long articleId = createArticleForTests(token);
    long id = createCommandeFournisseur(token, fournisseurId, articleId, uniqueCode("CMF"), 5);

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/commandesfournisseurs/lignesCommande/" + id)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lines = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertEquals(1, lines.size());
    JsonNode line = lines.get(0);
    org.junit.Assert.assertEquals(5, line.get("quantite").asInt());
    org.junit.Assert.assertNull("quantiteRecue ne doit pas apparaitre dans le contrat legacy",
        line.get("quantiteRecue"));
    org.junit.Assert.assertNull("quantiteRestanteARecevoir ne doit pas apparaitre dans le contrat legacy",
        line.get("quantiteRestanteARecevoir"));
    org.junit.Assert.assertTrue("commandeFournisseur doit etre absent/null",
        line.get("commandeFournisseur") == null || line.get("commandeFournisseur").isNull());
  }

  @Test
  public void createWithUnknownFournisseurIdReturns404FournisseurNotFound() throws Exception {
    String token = adminToken();
    String payload = "{\"code\":\"" + uniqueCode("CMF") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"etatCommande\":\"EN_PREPARATION\",\"fournisseur\":{\"id\":999999999}}";

    mockMvc.perform(post("/gestiondestock/v1/commandesfournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("FOURNISSEUR_NOT_FOUND"));
  }

  @Test
  public void createWithDuplicateCodeSurfacesAsRaw500WithoutErrorCode() throws Exception {
    String token = adminToken();
    long fournisseurId = createFournisseur(token);
    Long articleId = createArticleForTests(token);
    String code = uniqueCode("CMF");

    createCommandeFournisseur(token, fournisseurId, articleId, code, 1);

    String payload = "{\"code\":\"" + code + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"fournisseur\":{\"id\":" + fournisseurId + "},\"ligneCommandeFournisseurs\":[{\"article\":{\"id\":" + articleId
        + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    mockMvc.perform(post("/gestiondestock/v1/commandesfournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").doesNotExist());
  }
}
