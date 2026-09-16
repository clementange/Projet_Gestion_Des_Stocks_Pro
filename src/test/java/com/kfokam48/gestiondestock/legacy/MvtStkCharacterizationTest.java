package com.kfokam48.gestiondestock.legacy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Phase 0 (filet de securite) : caracterisation du contrat HTTP legacy {@code /mvtstk/*}, qui
 * delegue depuis Phase 22 a {@code inventory.Stock}/{@code StockMovement}. Complete
 * {@link LegacyControllersIntegrationTest#mvtStkEntreeAndStockReelWorkOverHttp()} (entree +
 * stockreel, sans assertion de corps de reponse, inchange).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class MvtStkCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("mvtstk-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe MvtStk Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000045", null,
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

  private void assertStockReel(String token, Long articleId, int expected) throws Exception {
    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/mvtstk/stockreel/" + articleId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    BigDecimal actual = new BigDecimal(result.getResponse().getContentAsString());
    org.junit.Assert.assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual));
  }

  @Test
  public void entreeReturnsFullyPopulatedResponseBody() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    String payload = "{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":15,"
        + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}";

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.dateMvt").exists())
        .andExpect(jsonPath("$.quantite").value(15))
        .andExpect(jsonPath("$.article.id").value(articleId))
        .andExpect(jsonPath("$.typeMvt").value("ENTREE"))
        .andExpect(jsonPath("$.sourceMvt").value("COMMANDE_FOURNISSEUR"))
        .andExpect(jsonPath("$.idEntreprise").exists());
  }

  @Test
  public void sortieWithinAvailableStockDecrementsStock() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":10,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/sortie")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":4,"
                + "\"typeMvt\":\"SORTIE\",\"sourceMvt\":\"VENTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.typeMvt").value("SORTIE"));

    assertStockReel(token, articleId, 6);
  }

  @Test
  public void sortieExceedingAvailableStockIsRejected() throws Exception {
    // Changement de comportement assume vs legacy historique (Phase 22) : une sortie qui
    // depasserait le stock disponible est desormais refusee, au lieu de rendre un stock negatif.
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":5,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/sortie")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":999,"
                + "\"typeMvt\":\"SORTIE\",\"sourceMvt\":\"VENTE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STOCK_INSUFFICIENT"));
  }

  @Test
  public void correctionEndpointsIgnoreTheSignSentInThePayload() throws Exception {
    // Le signe de "quantite" dans le corps de la requete est ignore : c'est l'endpoint appele
    // (correctionpos vs correctionneg) qui determine le signe reellement applique.
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":10,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isOk());

    // quantite envoyee negative sur correctionpos : traitee comme +3 quand meme.
    mockMvc.perform(post("/gestiondestock/v1/mvtstk/correctionpos")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":-3,"
                + "\"typeMvt\":\"CORRECTION_POS\"}"))
        .andExpect(status().isOk());

    assertStockReel(token, articleId, 13);

    // quantite envoyee positive sur correctionneg : traitee comme -3 quand meme.
    mockMvc.perform(post("/gestiondestock/v1/mvtstk/correctionneg")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":3,"
                + "\"typeMvt\":\"CORRECTION_NEG\"}"))
        .andExpect(status().isOk());

    assertStockReel(token, articleId, 10);
  }

  @Test
  public void correctionNegExceedingPhysicalStockIsRejected() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":5,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/correctionneg")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":999,"
                + "\"typeMvt\":\"CORRECTION_NEG\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("STOCK_INSUFFICIENT"));
  }

  @Test
  public void entreeWithZeroQuantiteReturnsCleanValidationError() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":0,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MVT_STK_NOT_VALID"))
        .andExpect(jsonPath("$.errors[0]").value("Veuillez renseigner la quantite du mouvenent"));
  }

  @Test
  public void entreeWithNullTypeMvtSurfacesAsRaw500InsteadOfCleanValidationError() throws Exception {
    // Bug identifie, non corrige en Phase 0 (voir docs/migration-notes.md) : MvtStkValidator
    // appelle dto.getTypeMvt().name() sans garde null -> NullPointerException -> 500 brut, au
    // lieu d'un message de validation propre comme pour les autres champs manquants.
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":5,"
                + "\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  public void filterByArticleReturnsMovementsWithTypeMvtMapped() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":7,"
                + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}"))
        .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/mvtstk/filter/article/" + articleId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode movements = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertTrue(movements.isArray());
    org.junit.Assert.assertEquals(1, movements.size());
    org.junit.Assert.assertEquals("ENTREE", movements.get(0).get("typeMvt").asText());
  }
}
