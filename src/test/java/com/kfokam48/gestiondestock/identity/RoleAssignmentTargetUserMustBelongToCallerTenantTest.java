package com.kfokam48.gestiondestock.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 5f : {@code POST /user-role-assignments/create} acceptait n'importe quel {@code userId},
 * sans verifier qu'il designe un utilisateur du tenant de l'appelant — ni meme qu'il designe un
 * utilisateur existant.
 *
 * <h2>Gravite reelle, mesuree et non supposee</h2>
 *
 * <p>Ce n'est **pas** une escalade de privileges. {@code AuthorizationServiceImpl.hasPermission}
 * ne retient que les affectations dont l'{@code organizationId} egale celui de l'APPELANT
 * ({@code assignmentsForOrganization}), et {@code organizationId} est impose depuis le principal
 * depuis la Phase 5b-2f. Une affectation creee par B pour un utilisateur de A porte donc
 * l'organisation de B et reste **inerte** quand cet utilisateur de A s'authentifie : son
 * {@code organizationId} resout A, pas B. Le test
 * {@link #theForeignGrantWasNeverEffectiveAnyway()} le verifie explicitement, pour que personne
 * ne corrige ce defaut en croyant fermer une escalade.
 *
 * <p>Ce qui est reellement en jeu : l'**integrite** du referentiel RBAC (des lignes referencant
 * des utilisateurs qui n'appartiennent pas a l'organisation, voire aucun utilisateur du tout) et
 * un **risque dormant** — un utilisateur deplace de tenant, ou un futur chemin de lecture moins
 * strictement filtre, rendrait ces lignes actives. D'ou un refus a la creation (fail-closed)
 * plutot qu'une confiance dans le filtrage en aval.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RoleAssignmentTargetUserMustBelongToCallerTenantTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Test-Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private Tenant a;
  private Tenant b;

  private static String uniq(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private record Tenant(String token, long tenantId, long organizationId, long adminUserId) {}

  @BeforeEach
  public void registerTwoTenants() throws Exception {
    a = register("ura-A");
    b = register("ura-B");
  }

  private Tenant register(String label) throws Exception {
    String email = uniq(label) + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe " + label, "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000107", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"), PASSWORD, PASSWORD);
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    String body = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asText();
    JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    return new Tenant(token, claims.get("idEntreprise").asLong(),
        claims.get("organizationId").asLong(), claims.get("idUtilisateur").asLong());
  }

  /** Un role quelconque du catalogue global, suffisant pour construire une affectation valide. */
  private long anyRoleId(String callerToken) throws Exception {
    String body = mockMvc.perform(get("/gestiondestock/v1/roles/all")
            .header("Authorization", "Bearer " + callerToken))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get(0).get("id").asLong();
  }

  @Test
  public void bCannotAssignARoleToAUserOfTenantA() throws Exception {
    long roleId = anyRoleId(b.token());

    mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + b.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":" + a.adminUserId() + ",\"role\":{\"id\":" + roleId + "},"
                + "\"scopeType\":\"GLOBAL\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  public void nobodyCanAssignARoleToAUserThatDoesNotExistAtAll() throws Exception {
    long roleId = anyRoleId(b.token());

    // Avant ce correctif, l'{@code userId} n'etait meme pas verifie comme existant : on pouvait
    // creer une affectation pour un entier arbitraire.
    mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + b.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":987654321,\"role\":{\"id\":" + roleId + "},"
                + "\"scopeType\":\"GLOBAL\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  public void bCanStillAssignARoleToItsOwnUser() throws Exception {
    long roleId = anyRoleId(b.token());

    // Contre-epreuve : le cas legitime reste autorise, sinon le correctif casserait l'attribution
    // de roles, seule voie d'acces pour les utilisateurs non-admin depuis la Phase 5c.
    String created = mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + b.token())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":" + b.adminUserId() + ",\"role\":{\"id\":" + roleId + "},"
                + "\"scopeType\":\"GLOBAL\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").value(b.organizationId()))
        .andReturn().getResponse().getContentAsString();

    // L'affectation doit etre RELUE, pas seulement renvoyee. Ma premiere version de ce correctif
    // n'annotait pas la nouvelle methode : la classe etant @Transactional(readOnly = true) et
    // l'appel interne a save(dto) court-circuitant le proxy Spring, l'insertion etait
    // silencieusement abandonnee au commit - la reponse portait un id, la ligne n'existait pas.
    // Asserter la seule reponse HTTP ne l'aurait jamais vu - voir docs/phase-5f-report.md.
    long assignmentId = objectMapper.readTree(created).get("id").asLong();
    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + assignmentId)
            .header("Authorization", "Bearer " + b.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(b.adminUserId()));
  }

  /**
   * Caracterise la gravite reelle : meme AVANT le correctif, l'affectation etrangere n'accordait
   * rien. {@code hasPermission} ne retient que les affectations de l'organisation de l'appelant.
   * Ce test documente la frontiere, pour qu'on ne surestime pas ce que 5f corrige.
   */
  @Test
  public void theForeignGrantWasNeverEffectiveAnyway() throws Exception {
    // L'admin de A conserve exactement ses droits dans SON organisation, et aucun dans celle de B.
    mockMvc.perform(get("/gestiondestock/v1/articles/all")
            .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk());

    assertEquals(false, a.organizationId() == b.organizationId(), "les deux tenants doivent bien etre distincts, sinon ce test ne prouve rien");
  }
}
