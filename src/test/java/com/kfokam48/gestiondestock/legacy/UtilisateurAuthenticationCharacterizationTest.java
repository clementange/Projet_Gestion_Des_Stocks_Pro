package com.kfokam48.gestiondestock.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import com.kfokam48.gestiondestock.utils.JwtUtil;
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
 * Phase 4a, caracterisation prealable (etape 0) : le flux d'authentification legacy
 * (model.Utilisateur, /utilisateurs/*, /auth/authenticate) n'avait jusqu'ici AUCUN test dedie
 * (contrairement aux 7 autres adaptateurs legacy caracterises en Phase 0) - ce fichier comble ce
 * vide AVANT tout changement de code, pour servir de filet direct pendant la migration de
 * model.Utilisateur vers identity.User. Ecrit et verifie vert AVANT la migration, doit rester
 * vert et sans modification d'assertion apres.
 *
 * <p>Reproduit deliberement le comportement actuel, bugs inclus : {@link #changerMotDePasse...}
 * documente l'IDOR connu (aucune verification que l'appelant est la cible ou un admin) et
 * {@link #create...WithoutAnyPermission} documente l'absence de verification de permission sur
 * create/delete/find. Aucun des deux n'est corrige ici (voir docs/migration-notes.md).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class UtilisateurAuthenticationCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JwtUtil jwtUtil;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("auth-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Auth Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000021", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    return login(email, "Test-Passw0rd!");
  }

  private String login(String email, String password) throws Exception {
    String payload = "{\"login\":\"" + email + "\",\"password\":\"" + password + "\"}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private long createUser(String callerToken, String email, String password) throws Exception {
    String payload = "{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + email + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"" + password + "\","
        + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + callerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void loginSucceedsAndJwtCarriesExpectedClaims() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("login-ok") + "@test.local";
    long userId = createUser(adminToken, email, "Passw0rd!");

    String token = login(email, "Passw0rd!");

    assertEquals(email, jwtUtil.extractUsername(token));
    assertEquals(String.valueOf(userId), jwtUtil.extractIdUtilisateur(token));
    // Cree sans idEntreprise dans le payload : la reclamation doit rester absente (null), pas "0"
    // ni une chaine vide - comportement actuel documente dans ApplicationUserDetailsService.
    assertNull(jwtUtil.extractIdEntreprise(token));
  }

  @Test
  public void loginFailsWithWrongPasswordReturns400BadCredentials() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("login-bad") + "@test.local";
    createUser(adminToken, email, "Passw0rd!");

    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"WrongPassword!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
  }

  @Test
  public void loginFailsForUnknownEmailWith500NotBadCredentials() throws Exception {
    // Asymetrie actuelle, non corrigee ici : mauvais mot de passe -> 400 BAD_CREDENTIALS (via
    // BadCredentialsException, gere explicitement par RestExceptionHandler), mais email inconnu
    // -> EntityNotFoundException levee par UtilisateurServiceImpl.findByEmail() a l'interieur de
    // ApplicationUserDetailsService.loadUserByUsername() ; Spring Security n'attrape que
    // UsernameNotFoundException a cet endroit (DaoAuthenticationProvider.retrieveUser), donc notre
    // EntityNotFoundException remonte enveloppee en InternalAuthenticationServiceException ->
    // capturee uniquement par le handler generique -> 500, code JSON null (pas UTILISATEUR_NOT_FOUND).
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + uniqueCode("unknown") + "@test.local\",\"password\":\"whatever\"}"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  public void changerMotDePasseHasNoOwnershipCheckIdorReproducedAsIs() throws Exception {
    // Bug connu, delibere, non corrige ici (docs/migration-notes.md) : n'importe quel utilisateur
    // authentifie peut changer le mot de passe de n'importe quel autre utilisateur.
    String adminToken = adminToken();
    String attackerEmail = uniqueCode("attacker") + "@test.local";
    createUser(adminToken, attackerEmail, "Passw0rd!");
    String attackerToken = login(attackerEmail, "Passw0rd!");

    String victimEmail = uniqueCode("victim") + "@test.local";
    long victimId = createUser(adminToken, victimEmail, "OldPassw0rd!");

    String changePayload = "{\"id\":" + victimId + ",\"motDePasse\":\"NewPassw0rd!\",\"confirmMotDePasse\":\"NewPassw0rd!\"}";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/update/password")
            .header("Authorization", "Bearer " + attackerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(changePayload))
        .andExpect(status().isOk());

    // Le nouveau mot de passe fonctionne bien pour la victime : la modification a reellement eu lieu.
    String victimToken = login(victimEmail, "NewPassw0rd!");
    assertNotNull(victimToken);
  }

  @Test
  public void createRejectsDuplicateEmail() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("dup") + "@test.local";
    createUser(adminToken, email, "Passw0rd!");

    String payload = "{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + email + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Autre!Passw0rd\","
        + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UTILISATEUR_ALREADY_EXISTS"));
  }

  @Test
  public void createRejectsMissingRequiredFields() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UTILISATEUR_NOT_VALID"));
  }

  @Test
  public void findByIdUnknownReturnsUtilisateurNotFound() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/utilisateurs/0").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UTILISATEUR_NOT_FOUND"));
  }

  @Test
  public void findByEmailUnknownReturnsUtilisateurNotFound() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/utilisateurs/find/" + uniqueCode("ghost") + "@test.local")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("UTILISATEUR_NOT_FOUND"));
  }

  @Test
  public void findAllIncludesCreatedUsers() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("findall") + "@test.local";
    createUser(adminToken, email, "Passw0rd!");

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/utilisateurs/all").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    boolean found = false;
    for (JsonNode node : body) {
      if (email.equals(node.get("email").asText())) {
        found = true;
      }
    }
    org.junit.Assert.assertTrue("l'utilisateur cree doit apparaitre dans /utilisateurs/all", found);
  }

  @Test
  public void deleteThenFindByIdReturnsNotFound() throws Exception {
    String adminToken = adminToken();
    long userId = createUser(adminToken, uniqueCode("todelete") + "@test.local", "Passw0rd!");

    mockMvc.perform(delete("/gestiondestock/v1/utilisateurs/delete/" + userId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/utilisateurs/" + userId).header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  public void createDoesNotRequireAnySpecificPermissionForAnyAuthenticatedUser() throws Exception {
    // Bug connu, delibere, non corrige ici : create/delete/find n'ont aucune verification de
    // permission au-dela de "authenticated()" (SecurityConfiguration). Un utilisateur fraichement
    // cree, sans aucune UserRoleAssignment, peut quand meme creer un autre utilisateur.
    String adminToken = adminToken();
    String plainEmail = uniqueCode("plain") + "@test.local";
    createUser(adminToken, plainEmail, "Passw0rd!");
    String plainToken = login(plainEmail, "Passw0rd!");

    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + plainToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + uniqueCode("byplain") + "@test.local\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk());
  }

  @Test
  public void utilisateursCreateIsRejectedWithoutAuthentication() throws Exception {
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + uniqueCode("anon") + "@test.local\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isForbidden());
  }
}
