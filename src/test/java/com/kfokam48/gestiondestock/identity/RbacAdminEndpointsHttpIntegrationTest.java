package com.kfokam48.gestiondestock.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    EntrepriseDto entreprise = EntrepriseDto.builder()
        .nom("Societe RBAC HTTP")
        .description("x")
        .codeFiscal(uniqueCode("CF"))
        .email(email)
        .numTel("+237600000050")
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
}
