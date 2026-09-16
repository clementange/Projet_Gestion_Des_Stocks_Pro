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
 * Phase 0 (filet de securite) : caracterisation du contrat HTTP legacy {@code /clients/*}, qui
 * delegue depuis Phase 18 a {@code sales.Customer} (voir ClientServiceImpl). L'objectif n'est pas
 * de re-derivier les regles metier (elles vivent dans le module {@code sales}, deja testees), mais
 * de figer le comportement actuel du contrat HTTP legacy - y compris ses incoherences connues -
 * avant qu'un futur refactor ne les casse silencieusement. Complete
 * {@link LegacyControllersIntegrationTest#clientCrudFlowWorksOverHttp()} (create/get/delete/404
 * happy path), qui reste inchange.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class ClientCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("client-char-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Client Char Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000040", null,
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

  private long createClient(String token, String mail) throws Exception {
    String payload = "{\"nom\":\"Client HTTP\",\"prenom\":\"Test\",\"mail\":\"" + mail + "\","
        + "\"numTel\":\"600000030\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void createWithMissingNomReturnsCleanValidationError() throws Exception {
    // ClientValidator.validate : "nom" manquant -> une seule erreur, tout le reste du payload
    // est par ailleurs valide (isole precisement le message associe a "nom").
    String token = adminToken();
    String payload = "{\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("client") + "@test.local\","
        + "\"numTel\":\"600000030\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";

    mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_VALID"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0]").value("Veuillez renseigner le nom du client"));
  }

  @Test
  public void findByIdUnknownReturns404WithCustomerNotFoundCode() throws Exception {
    // Incoherence figee : /clients/{id} delegue a sales.Customer, et l'erreur 404 porte donc le
    // code CUSTOMER_NOT_FOUND (module neuf), jamais un code CLIENT_* (contrat legacy).
    String token = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/clients/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
  }

  @Test
  public void findAllReturnsRawArrayAcrossAllTenantsWithoutPagination() throws Exception {
    // Fuite de lecture cross-tenant figee telle quelle (pas corrigee en Phase 0) : deux clients
    // crees sous deux entreprises differentes sont tous les deux visibles via /clients/all avec
    // le token de l'une OU l'autre entreprise. Pas de pagination (reponse = tableau JSON brut).
    String tokenA = adminToken();
    String tokenB = adminToken();
    long clientA = createClient(tokenA, uniqueCode("clientA") + "@test.local");
    long clientB = createClient(tokenB, uniqueCode("clientB") + "@test.local");

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/clients/all").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode all = objectMapper.readTree(result.getResponse().getContentAsString());

    org.junit.Assert.assertTrue("la reponse /clients/all doit etre un tableau JSON brut, sans enveloppe de pagination",
        all.isArray());
    boolean containsA = false;
    boolean containsB = false;
    for (JsonNode node : all) {
      if (node.get("id").asLong() == clientA) {
        containsA = true;
      }
      if (node.get("id").asLong() == clientB) {
        containsB = true;
      }
    }
    org.junit.Assert.assertTrue("client de l'entreprise A absent de /clients/all", containsA);
    org.junit.Assert.assertTrue("client de l'entreprise B (autre tenant) visible depuis le token A : "
        + "fuite cross-tenant figee comme comportement actuel", containsB);
  }

  @Test
  public void deleteClientReferencedByCommandeClientIsRejected() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);
    long clientId = createClient(token, uniqueCode("client") + "@test.local");

    String payload = "{\"code\":\"" + uniqueCode("CMD") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"etatCommande\":\"EN_PREPARATION\",\"client\":{\"id\":" + clientId + "},"
        + "\"ligneCommandeClients\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/clients/delete/" + clientId).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_IN_USE"));
  }

  @Test
  public void createWithoutCodePostaleStillSucceedsBecauseOfAdresseValidatorBug() throws Exception {
    // AdresseValidator.validate revalide adresse1 au lieu de codePostale (copier-coller) : le
    // champ codePostale n'est en realite jamais controle. Comportement fige tel quel (bug non
    // corrige en Phase 0, voir docs/migration-notes.md).
    String token = adminToken();
    String payload = "{\"nom\":\"Client HTTP\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("client") + "@test.local\","
        + "\"numTel\":\"600000030\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\"}}";

    mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());
  }

  @Test
  public void twoClientsWithSameMailBothSucceedBecauseNoUniquenessCheck() throws Exception {
    String token = adminToken();
    String mail = uniqueCode("dup") + "@test.local";

    long first = createClient(token, mail);
    long second = createClient(token, mail);

    org.junit.Assert.assertNotEquals("deux clients distincts doivent tout de meme etre crees avec le meme mail",
        first, second);
  }
}
