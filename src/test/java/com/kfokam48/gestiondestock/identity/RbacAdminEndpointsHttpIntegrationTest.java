package com.kfokam48.gestiondestock.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.dto.AdresseDto;
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
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Regression Phase 15 : UserRoleAssignmentController/RoleController/PermissionController
 * n'appliquaient aucune permission avant Phase 15 — n'importe quel utilisateur authentifie
 * pouvait s'auto-attribuer n'importe quel role (auto-escalade de privileges). Ces tests, via le
 * vrai filtre de securite (MockMvc), prouvent que RBAC_MANAGE est desormais exige pour les
 * ecritures, et qu'un utilisateur legitime (ADMINISTRATEUR/GLOBAL, cree automatiquement a
 * l'inscription d'une Entreprise) continue de fonctionner normalement.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class RbacAdminEndpointsHttpIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String tokenFor(String email) throws Exception {
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe RBAC HTTP", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000050", null,
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

  private long idUtilisateurFromToken(String token) throws Exception {
    String payload = token.split("\\.")[1];
    byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
    return objectMapper.readTree(decoded).get("idUtilisateur").asLong();
  }

  @Test
  public void entrepriseAdminCanCreatePermissionsAndRoles() throws Exception {
    // Cree via le bootstrap RBAC (EntrepriseServiceImpl) : role ADMINISTRATEUR, scope GLOBAL.
    String adminToken = tokenFor(uniqueCode("admin") + "@test.local");

    mockMvc.perform(post("/gestiondestock/v1/permissions/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("PERM") + "\",\"description\":\"x\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/roles/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("ROLE") + "\",\"name\":\"Role HTTP\",\"permissions\":[]}"))
        .andExpect(status().isOk());
  }

  @Test
  public void secondEntrepriseAdminIsAlsoAutomaticallyGrantedRbacManage() throws Exception {
    // Chaque nouvelle Entreprise amorce son propre admin GLOBAL independamment des autres.
    String otherAdminToken = tokenFor(uniqueCode("admin2") + "@test.local");

    mockMvc.perform(post("/gestiondestock/v1/permissions/create")
            .header("Authorization", "Bearer " + otherAdminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("PERM2") + "\",\"description\":\"x\"}"))
        .andExpect(status().isOk());
  }

  @Test
  public void userWithNoRoleAssignmentCannotSelfGrantARole() throws Exception {
    // Cree un utilisateur "nu" (aucune UserRoleAssignment) via l'endpoint legacy, distinct du
    // bootstrap d'Entreprise, puis verifie qu'il ne peut ni administrer les permissions/roles,
    // ni surtout s'auto-attribuer un role via user-role-assignments/create.
    String adminToken = tokenFor(uniqueCode("admin3") + "@test.local");
    String plainEmail = uniqueCode("plain") + "@test.local";
    String createUserPayload = "{\"nom\":\"Plain\",\"prenom\":\"User\",\"email\":\"" + plainEmail + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
        + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createUserPayload))
        .andExpect(status().isOk());

    String loginPayload = "{\"login\":\"" + plainEmail + "\",\"password\":\"Passw0rd!\"}";
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    String plainToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

    mockMvc.perform(post("/gestiondestock/v1/permissions/create")
            .header("Authorization", "Bearer " + plainToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("SHOULD_FAIL") + "\",\"description\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERMISSION_ACCESS_DENIED"));

    mockMvc.perform(post("/gestiondestock/v1/user-role-assignments/create")
            .header("Authorization", "Bearer " + plainToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":999999,\"role\":{\"id\":1},\"scopeType\":\"GLOBAL\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_ROLE_ASSIGNMENT_ACCESS_DENIED"));
  }

  /**
   * Phase 5b-2c : avant ce correctif, GET /roles/{id}, /roles/all, /permissions/{id},
   * /permissions/all n'appliquaient aucune verification de permission (contrairement a
   * save/delete, deja gates) - violation directe du zero-tolerance CLAUDE.md sur les routes RBAC
   * "y compris les endpoints de lecture (GET)" - voir docs/phase-5b2c-report.md.
   */
  @Test
  public void plainUserCannotReadRolesOrPermissions() throws Exception {
    String adminToken = tokenFor(uniqueCode("admin4") + "@test.local");
    MvcResult permission = mockMvc.perform(post("/gestiondestock/v1/permissions/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("PERM4") + "\",\"description\":\"x\"}"))
        .andExpect(status().isOk()).andReturn();
    long permissionId = objectMapper.readTree(permission.getResponse().getContentAsString()).get("id").asLong();
    MvcResult role = mockMvc.perform(post("/gestiondestock/v1/roles/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + uniqueCode("ROLE4") + "\",\"name\":\"Role HTTP 4\",\"permissions\":[]}"))
        .andExpect(status().isOk()).andReturn();
    long roleId = objectMapper.readTree(role.getResponse().getContentAsString()).get("id").asLong();

    String plainEmail = uniqueCode("plain4") + "@test.local";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Plain\",\"prenom\":\"User\",\"email\":\"" + plainEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk());
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + plainEmail + "\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isOk()).andReturn();
    String plainToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

    mockMvc.perform(get("/gestiondestock/v1/permissions/" + permissionId).header("Authorization", "Bearer " + plainToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERMISSION_ACCESS_DENIED"));
    mockMvc.perform(get("/gestiondestock/v1/permissions/all").header("Authorization", "Bearer " + plainToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERMISSION_ACCESS_DENIED"));
    mockMvc.perform(get("/gestiondestock/v1/roles/" + roleId).header("Authorization", "Bearer " + plainToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROLE_ACCESS_DENIED"));
    mockMvc.perform(get("/gestiondestock/v1/roles/all").header("Authorization", "Bearer " + plainToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROLE_ACCESS_DENIED"));

    // Le meme admin (RBAC_MANAGE) continue de lire normalement.
    mockMvc.perform(get("/gestiondestock/v1/permissions/" + permissionId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    mockMvc.perform(get("/gestiondestock/v1/roles/" + roleId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  public void plainUserCannotReadUserRoleAssignments() throws Exception {
    String adminToken = tokenFor(uniqueCode("admin5") + "@test.local");
    long adminUserId = idUtilisateurFromToken(adminToken);

    String plainEmail = uniqueCode("plain5") + "@test.local";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Plain\",\"prenom\":\"User\",\"email\":\"" + plainEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk());
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + plainEmail + "\",\"password\":\"Passw0rd!\"}"))
        .andExpect(status().isOk()).andReturn();
    String plainToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/filter/user/" + adminUserId)
            .header("Authorization", "Bearer " + plainToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_ROLE_ASSIGNMENT_ACCESS_DENIED"));

    // Le meme admin (RBAC_MANAGE) continue de lire normalement sa propre affectation bootstrap.
    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/filter/user/" + adminUserId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  /**
   * Phase 5b-2c : le garde-fou d'appartenance d'organisation sur UserRoleAssignment doit etre
   * INDEPENDANT de hasPermission - un appelant avec RBAC_MANAGE en GLOBAL dans SA PROPRE
   * organisation (A) ne doit jamais pouvoir lire l'affectation bootstrap d'une AUTRE organisation
   * (B) simplement parce que hasPermission le laisserait passer sur la portee GLOBAL - meme
   * classe de garde-fou que requireSiteInOrganization (5b-2b). Reponse attendue : 404 masque
   * (USER_ROLE_ASSIGNMENT_NOT_FOUND), pas 400 ACCESS_DENIED - preuve que c'est bien le garde-fou
   * d'appartenance qui rejette, pas seulement la permission - voir docs/phase-5b2c-report.md.
   */
  @Test
  public void globalRbacManageCallerCannotReadAssignmentFromAnotherOrganization() throws Exception {
    String tokenOrgA = tokenFor(uniqueCode("orga") + "@test.local");
    String tokenOrgB = tokenFor(uniqueCode("orgb") + "@test.local");
    long orgBAdminUserId = idUtilisateurFromToken(tokenOrgB);

    MvcResult orgBAssignments = mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/filter/user/" + orgBAdminUserId)
            .header("Authorization", "Bearer " + tokenOrgB))
        .andExpect(status().isOk()).andReturn();
    JsonNode assignments = objectMapper.readTree(orgBAssignments.getResponse().getContentAsString());
    long orgBAssignmentId = assignments.get(0).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + orgBAssignmentId)
            .header("Authorization", "Bearer " + tokenOrgA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_ROLE_ASSIGNMENT_NOT_FOUND"));

    // Org B continue de lire sa propre affectation normalement.
    mockMvc.perform(get("/gestiondestock/v1/user-role-assignments/" + orgBAssignmentId)
            .header("Authorization", "Bearer " + tokenOrgB))
        .andExpect(status().isOk());
  }
}
