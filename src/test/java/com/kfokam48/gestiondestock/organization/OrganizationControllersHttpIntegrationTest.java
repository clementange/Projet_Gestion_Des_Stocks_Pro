package com.kfokam48.gestiondestock.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Couverture HTTP reelle (MockMvc) du module organization, jusqu'ici seulement teste au niveau
 * service Java. Inclut en particulier le test de non-regression de la Phase 15 :
 * WarehouseValidator verifiait dto.getSite().getType() (ce que le CLIENT affirme dans la
 * requete), pas le Site reellement persiste — un appelant HTTP legitime qui suit la convention
 * "reference par ID seul" (comme partout ailleurs dans cette API) se faisait rejeter a tort, et
 * un appelant malveillant pouvait mentir sur le type pour contourner l'invariant. La verification
 * est desormais faite dans WarehouseServiceImpl contre le Site charge depuis la BDD.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class OrganizationControllersHttpIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("org-admin") + "@test.local";
    EntrepriseDto entreprise = EntrepriseDto.builder()
        .nom("Societe Org HTTP Test")
        .description("x")
        .codeFiscal(uniqueCode("CF"))
        .email(email)
        .numTel("+237600000040")
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

  @Test
  public void fullOrganizationHierarchyCreationWorksOverHttp() throws Exception {
    String token = adminToken();

    MvcResult org = mockMvc.perform(post("/gestiondestock/v1/organizations/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Org HTTP\",\"active\":true}"))
        .andExpect(status().isOk())
        .andReturn();
    long orgId = jsonId(org);

    MvcResult city = mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Douala HTTP\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long cityId = jsonId(city);

    MvcResult site = mockMvc.perform(post("/gestiondestock/v1/sites/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("SITE") + "\",\"name\":\"Site HTTP\",\"type\":\"ENTREPOT\",\"active\":true,\"city\":{\"id\":" + cityId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long siteId = jsonId(site);

    mockMvc.perform(get("/gestiondestock/v1/sites/filter/city/" + cityId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(siteId));

    // Reference par ID seul, comme tout appelant HTTP legitime le ferait (pas de "type" fourni).
    mockMvc.perform(post("/gestiondestock/v1/warehouses/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("WH") + "\",\"name\":\"WH HTTP\",\"active\":true,\"site\":{\"id\":" + siteId + "}}"))
        .andExpect(status().isOk());

    // Suppression en cascade refusee tant que des enfants existent.
    mockMvc.perform(delete("/gestiondestock/v1/sites/delete/" + siteId).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SITE_ALREADY_IN_USE"));
  }

  @Test
  public void warehouseCreationRejectsSiteTypeSpoofedInPayload() throws Exception {
    // Regression Phase 15 : le site reel est BOUTIQUE, mais le payload pretend ENTREPOT. Doit
    // etre refuse (verification contre le Site persiste, pas contre le DTO fourni).
    String token = adminToken();
    MvcResult org = mockMvc.perform(post("/gestiondestock/v1/organizations/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Org Spoof\",\"active\":true}"))
        .andExpect(status().isOk())
        .andReturn();
    long orgId = jsonId(org);
    MvcResult city = mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Douala Spoof\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long cityId = jsonId(city);
    MvcResult boutique = mockMvc.perform(post("/gestiondestock/v1/sites/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("BTQ") + "\",\"name\":\"Boutique Spoof\",\"type\":\"BOUTIQUE\",\"active\":true,\"city\":{\"id\":" + cityId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long boutiqueId = jsonId(boutique);

    mockMvc.perform(post("/gestiondestock/v1/warehouses/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("WH") + "\",\"name\":\"Spoofed\",\"active\":true,"
                + "\"site\":{\"id\":" + boutiqueId + ",\"type\":\"ENTREPOT\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("WAREHOUSE_NOT_VALID"))
        .andExpect(jsonPath("$.errors[0]").value("Le site associe a un entrepot doit etre de type ENTREPOT"));
  }

  @Test
  public void organizationDeleteWithDependentCityIsRejected() throws Exception {
    String token = adminToken();
    MvcResult org = mockMvc.perform(post("/gestiondestock/v1/organizations/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Org NoDelete\",\"active\":true}"))
        .andExpect(status().isOk())
        .andReturn();
    long orgId = jsonId(org);
    mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"City NoDelete\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/organizations/delete/" + orgId).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_ALREADY_IN_USE"));
  }
}
