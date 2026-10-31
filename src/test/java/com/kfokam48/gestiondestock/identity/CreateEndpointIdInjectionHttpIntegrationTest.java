package com.kfokam48.gestiondestock.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
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
 * Phase 5b-2f : un {@code create} ne peut jamais etre un {@code update}. Tout endpoint de creation
 * qui laisse passer un {@code id} fourni par le client transforme son
 * {@code repository.save()} en MERGE JPA, donc en mise a jour d'une ressource existante -
 * potentiellement celle d'un autre tenant. Cette classe verrouille les deux endpoints d'identity
 * du perimetre ({@code /users/create}, {@code /user-role-assignments/create}) ;
 * {@code /sites/create} est verrouille par
 * {@code OrganizationControllersHttpIntegrationTest.createSiteMustNotOverwriteAnExistingSiteOfAnotherTenantViaClientSuppliedId}
 * - voir docs/phase-5b2f-report.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CreateEndpointIdInjectionHttpIntegrationTest extends AbstractIntegrationTest {

  private static final String CREATION_ID_FORBIDDEN = "CREATION_ID_FORBIDDEN";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** Inscrit un tenant complet (Organization miroir + admin ADMINISTRATEUR/GLOBAL) et se connecte. */
  private String adminToken() throws Exception {
    String email = uniqueCode("idinj-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe IdInjection", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000070", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());
    return login(email, "Test-Passw0rd!");
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private long claim(String token, String name) throws Exception {
    String payload = token.split("\\.")[1];
    return objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload)).get(name).asLong();
  }

  private long jsonId(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  // ---------------------------------------------------------------- /users/create

  /**
   * Preuve que l'email ET le mot de passe de la victime restent inchanges : la victime doit
   * pouvoir continuer a se connecter avec ses identifiants d'origine, et l'email choisi par
   * l'attaquant ne doit jamais devenir un identifiant valide.
   */
  @Test
  public void createUserMustNotOverwriteAnExistingUserViaClientSuppliedId() throws Exception {
    String tokenA = adminToken();
    long tenantA = claim(tokenA, "idEntreprise");

    String victimEmail = uniqueCode("victim") + "@test.local";
    String victimPassword = "Victim-Passw0rd!";
    MvcResult victim = mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Victime\",\"prenom\":\"User\",\"email\":\"" + victimEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + victimPassword + "\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"},"
                + "\"idEntreprise\":" + tenantA + "}"))
        .andExpect(status().isOk())
        .andReturn();
    long victimId = jsonId(victim);

    // Baseline : la victime se connecte bien avec ses identifiants d'origine.
    login(victimEmail, victimPassword);

    // L'attaque : un autre tenant reutilise l'id de la victime avec un email neuf (le pre-check
    // d'unicite d'email ne s'exclut pas lui-meme, donc un email jamais vu suffit a le passer).
    String tokenB = adminToken();
    String attackerEmail = uniqueCode("attacker") + "@test.local";
    String attackerPassword = "Attacker-Passw0rd!";
    mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + victimId + ",\"nom\":\"Detourne\",\"prenom\":\"ParB\","
                + "\"email\":\"" + attackerEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + attackerPassword + "\","
                + "\"adresse\":{\"adresse1\":\"y\",\"ville\":\"y\",\"pays\":\"y\",\"codePostale\":\"11111\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CREATION_ID_FORBIDDEN));

    // Email inchange ET mot de passe inchange : la victime se reconnecte normalement.
    login(victimEmail, victimPassword);

    // L'email de l'attaquant n'est jamais devenu un identifiant valide.
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + attackerEmail + "\",\"password\":\"" + attackerPassword + "\"}"))
        .andExpect(status().isBadRequest());

    // Et le mot de passe choisi par l'attaquant ne fonctionne pas sur le compte de la victime.
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + victimEmail + "\",\"password\":\"" + attackerPassword + "\"}"))
        .andExpect(status().isBadRequest());
  }

  // ------------------------------------------- /user-role-assignments/create

  private long createAssignment(String token, long userId, String scopeType) throws Exception {
    // organizationId est fourni ici parce que UserRoleAssignmentValidator l'exige depuis la
    // Phase 5b-1 ; c'est la valeur que le principal porte deja, donc le forcage ajoute en 5b-2f
    // la reecrit a l'identique. Le fait qu'une valeur ETRANGERE soit ignoree est verifie
    // separement par createAssignmentMustIgnoreClientSuppliedOrganizationId.
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":" + userId + ",\"role\":{\"id\":" + adminRoleId(token) + "},"
                + "\"scopeType\":\"" + scopeType + "\","
                + "\"organizationId\":" + claim(token, "organizationId") + "}"))
        .andExpect(status().isOk())
        .andReturn();
    return jsonId(result);
  }

  /** Le role ADMINISTRATEUR seede par Flyway, recupere via l'affectation bootstrap de l'admin. */
  private long adminRoleId(String token) throws Exception {
    long adminUserId = claim(token, "idUtilisateur");
    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/filter/user/" + adminUserId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("role").get("id").asLong();
  }

  @Test
  public void createAssignmentMustNotOverwriteAnExistingAssignmentOfAnotherTenantViaClientSuppliedId() throws Exception {
    String tokenA = adminToken();
    long userA = claim(tokenA, "idUtilisateur");
    long assignmentA = createAssignment(tokenA, userA, "GLOBAL");

    String tokenB = adminToken();
    long userB = claim(tokenB, "idUtilisateur");

    // organizationId est fourni, comme le ferait un vrai attaquant : sans lui la requete serait
    // rejetee par UserRoleAssignmentValidator (5b-1) pour une raison sans rapport avec l'id, ce
    // qui masquerait la faille au lieu de la prouver.
    mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":" + assignmentA + ",\"userId\":" + userB + ","
                + "\"role\":{\"id\":" + adminRoleId(tokenB) + "},\"scopeType\":\"GLOBAL\","
                + "\"organizationId\":" + claim(tokenB, "organizationId") + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(CREATION_ID_FORBIDDEN));

    // L'affectation de A est intacte et toujours la sienne.
    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + assignmentA)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userA));
  }

  /**
   * Phase 5b-2f : {@code organizationId} ne doit plus venir du client. Sans forcage, un admin de
   * tenant pouvait creer une affectation portant l'organisation d'un AUTRE tenant - donc accorder
   * un role dans une organisation qui n'est pas la sienne.
   */
  @Test
  public void createAssignmentMustIgnoreClientSuppliedOrganizationId() throws Exception {
    String tokenA = adminToken();
    long orgA = claim(tokenA, "organizationId");

    String tokenB = adminToken();
    long userB = claim(tokenB, "idUtilisateur");

    // B tente d'ancrer son affectation dans l'organisation de A.
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":" + userB + ",\"role\":{\"id\":" + adminRoleId(tokenB) + "},"
                + "\"scopeType\":\"GLOBAL\",\"organizationId\":" + orgA + "}"))
        .andExpect(status().isOk())
        .andReturn();
    long assignmentId = jsonId(created);

    // L'organisation retenue est celle de B (son JWT), pas celle demandee dans le corps : A ne
    // doit donc pas pouvoir lire cette affectation (lecture org-scopee depuis 5b-2c).
    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + assignmentId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + assignmentId)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk());
  }
}
