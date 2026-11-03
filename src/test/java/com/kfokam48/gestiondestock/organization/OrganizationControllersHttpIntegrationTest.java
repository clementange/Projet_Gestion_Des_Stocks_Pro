package com.kfokam48.gestiondestock.organization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Couverture HTTP reelle (MockMvc) du module organization, jusqu'ici seulement teste au niveau
 * service Java. Inclut en particulier le test de non-regression de la Phase 15 :
 * WarehouseValidator verifiait dto.getSite().getType() (ce que le CLIENT affirme dans la
 * requete), pas le Site reellement persiste — un appelant HTTP legitime qui suit la convention
 * "reference par ID seul" (comme partout ailleurs dans cette API) se faisait rejeter a tort, et
 * un appelant malveillant pouvait mentir sur le type pour contourner l'invariant. La verification
 * est desormais faite dans WarehouseServiceImpl contre le Site charge depuis la BDD.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class OrganizationControllersHttpIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("org-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Org HTTP Test", "x",
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

  private long jsonId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  /**
   * Phase 5b-2b : GET /sites/filter/city/{id} est desormais scope a l'organisation de l'appelant
   * (celle resolue a l'inscription du tenant) - voir docs/phase-5b2b-report.md. organizationId
   * n'est pas expose par un endpoint dedie : on le decode directement du JWT.
   */
  private long organizationIdFromToken(String token) throws Exception {
    String payload = token.split("\\.")[1];
    byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
    return objectMapper.readTree(decoded).get("organizationId").asLong();
  }

  @Test
  public void fullOrganizationHierarchyCreationWorksOverHttp() throws Exception {
    String token = adminToken();
    long orgId = organizationIdFromToken(token);

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
    // Phase 5b-2d : la ville doit etre creee sous l'organisation propre de l'appelant (decodee du
    // JWT), pas une organisation fraichement creee via /organizations/create - meme cause racine
    // que le correctif de fixture documente en 5b-2b/5b-2c - voir docs/phase-5b2d-report.md.
    String token = adminToken();
    long orgId = organizationIdFromToken(token);
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
    // Phase 5b-2c : delete est desormais self-only - la ville doit etre creee sous l'organisation
    // propre de l'appelant (decodee du JWT) - voir docs/phase-5b2c-report.md. Phase 5b-2e :
    // /organizations/create, qui servait auparavant a fabriquer une organisation de test
    // deconnectee du principal, n'existe plus du tout - voir docs/phase-5b2e-report.md.
    String token = adminToken();
    long orgId = organizationIdFromToken(token);
    mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"City NoDelete\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/organizations/delete/" + orgId).header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORGANIZATION_ALREADY_IN_USE"));
  }

  /**
   * Phase 5b-2e : POST /organizations/create est supprime. Une Organization creee par cette route
   * n'avait ni Tenant, ni admin, ni affectation RBAC - une orpheline inatteignable, cause racine
   * de trois regressions de fixture consecutives. Le seul chemin de creation restant est
   * POST /tenants/register (exerce par adminToken() ci-dessus, et par
   * EntrepriseServiceOrganizationMirrorIntegrationTest pour l'Organization miroir) - voir
   * docs/phase-5b2e-report.md.
   */
  @Test
  public void organizationCreateEndpointNoLongerExists() throws Exception {
    String token = adminToken();

    mockMvc.perform(post("/gestiondestock/v1/organizations/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Org Fantome\",\"active\":true}"))
        .andExpect(status().is4xxClientError());

    // Les autres routes du controleur restent operationnelles : la suppression est bien ciblee
    // sur la seule creation, pas sur l'ensemble du controleur.
    long orgId = organizationIdFromToken(token);
    mockMvc.perform(get("/gestiondestock/v1/organizations/" + orgId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orgId));
  }

  /** Cree une ville appartenant a l'organisation de l'appelant et retourne son id. */
  private long createCity(String token, String name) throws Exception {
    long orgId = organizationIdFromToken(token);
    MvcResult city = mockMvc.perform(post("/gestiondestock/v1/cities/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"organization\":{\"id\":" + orgId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    return jsonId(city);
  }

  /**
   * Phase 5b-2f : POST /sites/create acceptait un {@code id} fourni par le client
   * ({@code SiteDto.toEntity} fait {@code setId}), et {@code siteRepository.save()} sur une entite
   * portant un id deja existant est un MERGE, donc une mise a jour. Un utilisateur du tenant B
   * pouvait ainsi ECRASER un site du tenant A en fournissant l'id de A et une ville lui
   * appartenant, a lui, B : la verification d'appartenance de City posee en 5b-2d ne regarde que
   * la ville, jamais l'entite ciblee par l'id. Ecriture inter-tenant, pas une simple fuite de
   * lecture - voir docs/phase-5b2f-report.md.
   */
  @Test
  public void createSiteMustNotOverwriteAnExistingSiteOfAnotherTenantViaClientSuppliedId() throws Exception {
    // Tenant A : un site legitime, qu'on ne doit pas pouvoir toucher depuis un autre tenant.
    String tokenA = adminToken();
    long cityA = createCity(tokenA, "Douala A");
    String codeA = uniqueCode("SITE-A");
    MvcResult siteAResult = mockMvc.perform(post("/gestiondestock/v1/sites/create")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + codeA + "\",\"name\":\"Site A legitime\",\"type\":\"ENTREPOT\","
                + "\"active\":true,\"city\":{\"id\":" + cityA + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long siteAId = jsonId(siteAResult);

    // Tenant B : sa propre ville, donc le controle City de 5b-2d passe sans probleme.
    String tokenB = adminToken();
    long cityB = createCity(tokenB, "Yaounde B");

    // L'attaque : B reutilise l'id du site de A. Doit etre refuse explicitement.
    mockMvc.perform(post("/gestiondestock/v1/sites/create")
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + siteAId + ",\"code\":\"" + uniqueCode("HIJACK") + "\","
                + "\"name\":\"Site detourne par B\",\"type\":\"BOUTIQUE\","
                + "\"active\":false,\"city\":{\"id\":" + cityB + "}}"))
        .andExpect(status().isBadRequest())
        // Phase 5b-2f : code transverse du garde-fou central (CreationIdGuard), pas SITE_NOT_VALID
        // - l'invariant « un create n'est jamais un update » est le meme pour tous les endpoints.
        .andExpect(jsonPath("$.code").value("CREATION_ID_FORBIDDEN"));

    // Le site de A doit etre strictement inchange, et toujours visible par A (donc toujours
    // rattache a une ville de A : s'il avait ete repointe sur la ville de B, la lecture
    // org-scopee de A renverrait 404).
    mockMvc.perform(get("/gestiondestock/v1/sites/" + siteAId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(codeA))
        .andExpect(jsonPath("$.name").value("Site A legitime"))
        .andExpect(jsonPath("$.type").value("ENTREPOT"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.city.id").value(cityA));
  }
}
