package com.kfokam48.gestiondestock.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.dto.EntrepriseDto;
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
 * Couverture HTTP reelle (MockMvc, vrai filtre de securite + vraie resolution du principal) des
 * modules inventory/purchasing/sales/transfers/reporting, jusqu'ici testes uniquement au niveau
 * service Java (userId passe directement en argument par le test, jamais resolu depuis un
 * contexte d'authentification reel).
 *
 * <p>Couvre en particulier la regression Phase 15 : ReportingController prenait "userId" en
 * @RequestParam fourni par l'appelant au lieu de le resoudre depuis le principal authentifie —
 * n'importe quel appelant pouvait ainsi usurper l'identite de n'importe quel utilisateur pour les
 * besoins d'AuthorizationService. Corrige avec @AuthenticationPrincipal.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class BusinessModulesHttpIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("biz-admin") + "@test.local";
    EntrepriseDto entreprise = EntrepriseDto.builder()
        .nom("Societe Business HTTP")
        .description("x")
        .codeFiscal(uniqueCode("CF"))
        .email(email)
        .numTel("+237600000060")
        .adresse(AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build();
    mockMvc.perform(post("/gestiondestock/v1/entreprises/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(entreprise)))
        .andExpect(status().isOk());
    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"som3R@nd0mP@$$word\"}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private long jsonId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private long createEntrepotSite(String token, String orgName) throws Exception {
    MvcResult org = mockMvc.perform(post("/gestiondestock/v1/organizations/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + orgName + "\",\"active\":true}"))
        .andExpect(status().isOk()).andReturn();
    long orgId = jsonId(org);
    MvcResult city = mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Douala\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk()).andReturn();
    long cityId = jsonId(city);
    MvcResult site = mockMvc.perform(post("/gestiondestock/v1/sites/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("SITE") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\",\"active\":true,\"city\":{\"id\":" + cityId + "}}"))
        .andExpect(status().isOk()).andReturn();
    return jsonId(site);
  }

  private long createArticle(String token) throws Exception {
    String catCode = uniqueCode("CAT");
    mockMvc.perform(post("/gestiondestock/v1/categories/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + catCode + "\",\"designation\":\"Cat\"}"))
        .andExpect(status().isOk());
    JsonNode cat = objectMapper.readTree(mockMvc.perform(get("/gestiondestock/v1/categories/filter/" + catCode)
            .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
    MvcResult article = mockMvc.perform(post("/gestiondestock/v1/articles/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"codeArticle\":\"" + uniqueCode("ART") + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + cat.get("id").asLong() + "}}"))
        .andExpect(status().isOk()).andReturn();
    return jsonId(article);
  }

  @Test
  public void fullSupplyChainFlowWorksOverRealHttpWithAuthenticatedPrincipal() throws Exception {
    String token = adminToken();
    long siteId = createEntrepotSite(token, "Org Business");
    long articleId = createArticle(token);

    // inventory : entree directe
    mockMvc.perform(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId + "/entree?quantite=100&reference=INIT")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // purchasing : PURCHASE_ORDER_RECEIVE resolu depuis le principal, pas depuis un parametre
    MvcResult po = mockMvc.perform(post("/gestiondestock/v1/purchase-orders/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"order\":{\"code\":\"" + uniqueCode("PO") + "\",\"supplierId\":1,\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantiteCommandee\":10,\"prixUnitaire\":90}]}"))
        .andExpect(status().isOk()).andReturn();
    long poId = jsonId(po);
    mockMvc.perform(post("/gestiondestock/v1/purchase-orders/" + poId + "/valider").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    JsonNode lignes = objectMapper.readTree(mockMvc.perform(get("/gestiondestock/v1/purchase-orders/" + poId + "/lignes")
            .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString());
    long ligneId = lignes.get(0).get("id").asLong();
    mockMvc.perform(post("/gestiondestock/v1/purchase-orders/" + poId + "/lignes/" + ligneId + "/receptionner?quantite=10")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantiteRecue").value(10.0));

    // sales : SALE_CREATE resolu depuis le principal
    mockMvc.perform(post("/gestiondestock/v1/sales/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sale\":{\"code\":\"" + uniqueCode("SALE") + "\",\"site\":{\"id\":" + siteId + "}},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":5,\"prixUnitaire\":119.25}]}"))
        .andExpect(status().isOk());

    // reporting : userId resolu depuis le principal (regression Phase 15)
    mockMvc.perform(get("/gestiondestock/v1/reporting/stock/site/" + siteId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].quantitePhysique").value(105.0));
  }

  @Test
  public void stockTransferFullLifecycleWorksOverRealHttp() throws Exception {
    String token = adminToken();
    long originSiteId = createEntrepotSite(token, "Org Transfer Origin");
    long destSiteId = createEntrepotSite(token, "Org Transfer Dest");
    long articleId = createArticle(token);

    mockMvc.perform(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + originSiteId + "/entree?quantite=50&reference=INIT")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    MvcResult transfer = mockMvc.perform(post("/gestiondestock/v1/stock-transfers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"transfer\":{\"code\":\"" + uniqueCode("TR") + "\",\"originSite\":{\"id\":" + originSiteId + "},"
                + "\"destinationSite\":{\"id\":" + destSiteId + "},\"requestedByUserId\":1},"
                + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":20}]}"))
        .andExpect(status().isOk()).andReturn();
    long trId = jsonId(transfer);

    mockMvc.perform(post("/gestiondestock/v1/stock-transfers/" + trId + "/demander").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc.perform(post("/gestiondestock/v1/stock-transfers/" + trId + "/approuver").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mockMvc.perform(post("/gestiondestock/v1/stock-transfers/" + trId + "/preparer").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    // STOCK_TRANSFER_SHIP resolu depuis le principal, verifie sur le site origine
    mockMvc.perform(post("/gestiondestock/v1/stock-transfers/" + trId + "/expedier").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPEDIE"));
    // STOCK_TRANSFER_RECEIVE resolu depuis le principal, verifie sur le site destination
    mockMvc.perform(post("/gestiondestock/v1/stock-transfers/" + trId + "/receptionner").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECU"));

    mockMvc.perform(get("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + destSiteId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantitePhysique").value(20.0));
  }

  @Test
  public void reportingRejectsUserWithoutReportingViewEvenIfTheyClaimAnotherUserId() throws Exception {
    // Avant Phase 15, "userId" etait un @RequestParam controle par l'appelant : un utilisateur
    // sans REPORTING_VIEW pouvait passer l'ID d'un utilisateur GLOBAL pour lire les rapports a sa
    // place. Desormais resolu depuis le principal authentifie : impossible d'usurper.
    String plainAdminToken = adminToken(); // a REPORTING_VIEW via ADMINISTRATEUR/GLOBAL
    String noPermEmail = uniqueCode("noperm") + "@test.local";
    // Utilisateur cree via /utilisateurs/create : aucune UserRoleAssignment.
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + plainAdminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"NoPerm\",\"prenom\":\"User\",\"email\":\"" + noPermEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk());
    MvcResult login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + noPermEmail + "\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isOk()).andReturn();
    String noPermToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

    mockMvc.perform(get("/gestiondestock/v1/reporting/stock/global").header("Authorization", "Bearer " + noPermToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REPORTING_ACCESS_DENIED"));
  }
}
