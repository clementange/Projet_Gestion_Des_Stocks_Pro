package com.kfokam48.gestiondestock.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import com.kfokam48.gestiondestock.utils.JwtUtil;
import java.time.Instant;
import java.util.Base64;
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
 * Caracterisation du flux d'authentification ({@code /auth/authenticate}) et du cycle de vie
 * utilisateur sur le contrat canonique {@code /users/**}.
 *
 * <p>Successeur direct de {@code legacy.UtilisateurAuthenticationCharacterizationTest}, ecrit en
 * Phase 4a pour servir de filet pendant la migration de {@code model.Utilisateur} vers
 * {@code identity.User}. La Phase 4e ayant supprime le contrat {@code /utilisateurs/**} que ce
 * filet protegeait, chaque cas a ete **porte** sur {@code /users/**} plutot que supprime : les
 * assertions sont les memes, seuls l'URL, le nom du champ de mot de passe
 * ({@code moteDePasse} -> {@code motDePasse}), la reference au tenant
 * ({@code entreprise:{id}} -> {@code idEntreprise}) et le prefixe des codes d'erreur
 * ({@code UTILISATEUR_*} -> {@code USER_*}) changent - voir docs/api-migration-map.md §3.
 *
 * <p>Un seul cas n'a pas ete porte : {@code createDoesNotRequireAnySpecificPermission...}, qui
 * verrouillait deliberement un BUG (aucune verification de permission sur la creation
 * d'utilisateur). La Phase 5c a corrige ce bug ; le comportement inverse est desormais prouve par
 * {@code integration.WritePermissionEnforcementHttpIntegrationTest}. Porter ce test aurait
 * consiste a re-affirmer une faille fermee - voir docs/phase-4e-report.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class AuthenticationCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JwtUtil jwtUtil;

  @Autowired
  private UserService userService;

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

  private long idEntrepriseFromToken(String token) throws Exception {
    byte[] decoded = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
    return objectMapper.readTree(decoded).get("idEntreprise").asLong();
  }

  /**
   * Phase 5d : {@code /users/create} impose le tenant de l'appelant, donc ce helper cree
   * desormais l'utilisateur DANS le tenant de l'appelant, quoi que contienne le corps. Les tests
   * qui en dependent ({@code login*}, {@code passwordChange*}) ne caracterisent pas le tenant,
   * seulement l'authentification - leurs assertions sont inchangees.
   */
  private long createUser(String callerToken, String email, String password) throws Exception {
    return createUser(callerToken, email, password, null);
  }

  /**
   * Cree un utilisateur SANS aucun tenant, via la variante interne du service - le seul chemin qui
   * le permette encore depuis la Phase 5d. Utilise par
   * {@link #loginOfATenantlessUserCarriesNoIdEntrepriseClaim()}, qui caracterise precisement ce
   * cas limite (la reclamation doit etre absente, pas "0" ni une chaine vide).
   */
  private long createTenantlessUser(String email, String password) {
    return userService.save(UserDto.builder()
        .nom("Nom").prenom("Prenom").email(email)
        .dateDeNaissance(Instant.parse("1990-01-01T00:00:00Z"))
        .motDePasse(password)
        .adresse(AdresseDto.builder().adresse1("x").ville("x").pays("x").codePostale("00000").build())
        .build()).getId();
  }

  /**
   * Cree l'utilisateur DANS le tenant de l'appelant : utilise par les tests qui relisent ensuite
   * la ressource, les lectures de {@code /users/**} etant scopees au tenant depuis la Phase 5b-2c.
   */
  private long createUserInCallerTenant(String callerToken, String email, String password) throws Exception {
    return createUser(callerToken, email, password, idEntrepriseFromToken(callerToken));
  }

  private long createUser(String callerToken, String email, String password, Long tenantId) throws Exception {
    String payload = "{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + email + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + password + "\","
        + (tenantId == null ? "" : "\"idEntreprise\":" + tenantId + ",")
        + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + callerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  // ------------------------------------------------------------ authentification

  @Test
  public void loginSucceedsAndJwtCarriesExpectedClaims() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("login-ok") + "@test.local";
    long userId = createUser(adminToken, email, "Passw0rd!");

    String token = login(email, "Passw0rd!");

    assertEquals(email, jwtUtil.extractUsername(token));
    assertEquals(String.valueOf(userId), jwtUtil.extractIdUtilisateur(token));
    // Phase 5d : l'utilisateur rejoint le tenant de l'appelant, la reclamation le porte donc
    // desormais. L'absence de reclamation reste caracterisee ci-dessous, sur le seul cas qui peut
    // encore la produire.
    assertNotNull(jwtUtil.extractIdEntreprise(token));
  }

  @Test
  public void loginOfATenantlessUserCarriesNoIdEntrepriseClaim() throws Exception {
    // Assertion inchangee depuis la Phase 4a ; seule la fixture a change (voir
    // createTenantlessUser). Elle verrouille une NullPointerException reelle de
    // ApplicationUserDetailsService/JwtUtil sur un utilisateur sans Entreprise.
    String email = uniqueCode("tenantless") + "@test.local";
    createTenantlessUser(email, "Passw0rd!");

    String token = login(email, "Passw0rd!");

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
  public void loginFailsForUnknownEmailWith400BadCredentialsSameAsWrongPassword() throws Exception {
    // Phase 5a : l'asymetrie precedente (email inconnu -> 500 brut, mot de passe faux -> 400
    // BAD_CREDENTIALS) etait aussi un oracle d'enumeration de compte via le code HTTP seul -
    // corrigee dans ApplicationUserDetailsService.loadUserByUsername (voir docs/phase-5a-report.md).
    // Les deux cas sont desormais indiscernables cote client, comme il se doit.
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + uniqueCode("unknown") + "@test.local\",\"password\":\"whatever\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
  }

  // --------------------------------------------------------- mot de passe (self-only)

  @Test
  public void passwordChangeRejectsWhenCallerIsNotTargetUser() throws Exception {
    // Phase 5a : l'IDOR est corrige (self-only) - voir docs/phase-5a-report.md. L'appelant ne peut
    // plus changer le mot de passe d'un autre utilisateur, et le mot de passe de la victime n'est
    // pas modifie.
    String adminToken = adminToken();
    String attackerEmail = uniqueCode("attacker") + "@test.local";
    createUser(adminToken, attackerEmail, "Passw0rd!");
    String attackerToken = login(attackerEmail, "Passw0rd!");

    String victimEmail = uniqueCode("victim") + "@test.local";
    long victimId = createUser(adminToken, victimEmail, "OldPassw0rd!");

    mockMvc.perform(post("/gestiondestock/v1/users/" + victimId + "/password")
            .header("Authorization", "Bearer " + attackerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motDePasse\":\"NewPassw0rd!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_CHANGE_PASSWORD_FORBIDDEN"));

    // Le mot de passe de la victime n'a pas change : l'ancien fonctionne toujours.
    assertNotNull(login(victimEmail, "OldPassw0rd!"));
  }

  @Test
  public void passwordChangeSucceedsWhenCallerIsTargetUser() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("self") + "@test.local";
    long userId = createUser(adminToken, email, "OldPassw0rd!");
    String ownToken = login(email, "OldPassw0rd!");

    mockMvc.perform(post("/gestiondestock/v1/users/" + userId + "/password")
            .header("Authorization", "Bearer " + ownToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motDePasse\":\"NewPassw0rd!\"}"))
        .andExpect(status().isOk());

    assertNotNull(login(email, "NewPassw0rd!"));
  }

  // ------------------------------------------------------- cycle de vie utilisateur

  @Test
  public void createRejectsDuplicateEmail() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("dup") + "@test.local";
    createUser(adminToken, email, "Passw0rd!");

    mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + email + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"Autre!Passw0rd\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
  }

  @Test
  public void createRejectsMissingRequiredFields() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_NOT_VALID"));
  }

  @Test
  public void findByIdUnknownReturnsUserNotFound() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/users/0").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  public void findByEmailUnknownReturnsUserNotFound() throws Exception {
    String adminToken = adminToken();

    mockMvc.perform(get("/gestiondestock/v1/users/find/" + uniqueCode("ghost") + "@test.local")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
  }

  @Test
  public void findAllIncludesCreatedUsers() throws Exception {
    String adminToken = adminToken();
    String email = uniqueCode("findall") + "@test.local";
    createUserInCallerTenant(adminToken, email, "Passw0rd!");

    MvcResult result = mockMvc.perform(get("/gestiondestock/v1/users/all")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    boolean found = false;
    for (JsonNode node : body) {
      if (email.equals(node.get("email").asText())) {
        found = true;
      }
    }
    assertTrue("l'utilisateur cree doit apparaitre dans /users/all", found);
  }

  @Test
  public void deleteThenFindByIdReturnsNotFound() throws Exception {
    String adminToken = adminToken();
    long userId = createUserInCallerTenant(adminToken, uniqueCode("todelete") + "@test.local", "Passw0rd!");

    mockMvc.perform(delete("/gestiondestock/v1/users/delete/" + userId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/users/" + userId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isNotFound());
  }

  @Test
  public void createIsRejectedWithoutAuthentication() throws Exception {
    mockMvc.perform(post("/gestiondestock/v1/users/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Nom\",\"prenom\":\"Prenom\",\"email\":\"" + uniqueCode("anon") + "@test.local\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"Passw0rd!\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isForbidden());
  }
}
