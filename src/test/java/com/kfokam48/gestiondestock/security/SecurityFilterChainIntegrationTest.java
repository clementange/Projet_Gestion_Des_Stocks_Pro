package com.kfokam48.gestiondestock.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Ces tests passent par le vrai filtre de securite (MockMvc + DispatcherServlet reel), pas par
 * un appel direct au service comme le reste de la suite. C'est deliberement different : Phase 15
 * (audit curl manuel sur une instance reellement demarree) a trouve plusieurs bugs INVISIBLES a
 * @SpringBootTest classique appelant les services Java directement :
 * <ul>
 *   <li>"/**\/authenticate" et "/**\/entreprises/create" dans SecurityConfiguration levaient
 *   PatternParseException sur CHAQUE requete reelle (Spring 6 PathPattern interdit "**" ailleurs
 *   qu'en fin de motif) — l'application entiere etait cassee en conditions reelles ;</li>
 *   <li>la cle de signature JWT ("secret", 48 bits) faisait echouer TOUTE authentification
 *   (io.jsonwebtoken.security.WeakKeyException, jjwt 0.11.5 exige >= 256 bits pour HS256).</li>
 * </ul>
 * Ces deux bugs ne pouvaient etre detectes qu'en passant reellement par le filtre de securite et
 * la generation de JWT, d'ou ces tests MockMvc.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class SecurityFilterChainIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueEmail() {
    return "sec-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
  }

  @Test
  public void permitAllPathsAreReachableWithoutPatternParseException() throws Exception {
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  public void protectedEndpointRejectsRequestWithoutToken() throws Exception {
    mockMvc.perform(get("/gestiondestock/v1/organizations/all"))
        .andExpect(status().isForbidden());
  }

  @Test
  public void protectedEndpointRejectsInvalidToken() throws Exception {
    mockMvc.perform(get("/gestiondestock/v1/organizations/all")
            .header("Authorization", "Bearer not-a-real-jwt"))
        .andExpect(status().isForbidden());
  }

  @Test
  public void fullLoginFlowThroughRealFilterChainIssuesAWorkingJwt() throws Exception {
    String email = uniqueEmail();

    EntrepriseDto entreprise = EntrepriseDto.builder()
        .nom("Societe Security Test")
        .description("Test filtre de securite")
        .codeFiscal("CF-SEC-" + UUID.randomUUID().toString().substring(0, 8))
        .email(email)
        .numTel("+237600000010")
        .adresse(AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build();

    // "/entreprises/create" est justement l'un des deux chemins qui levaient
    // PatternParseException avant le fix : l'appeler ici via MockMvc (permitAll, sans token)
    // est en soi une garantie de non-regression.
    mockMvc.perform(post("/gestiondestock/v1/entreprises/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(entreprise)))
        .andExpect(status().isOk());

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"som3R@nd0mP@$$word\"}";

    // "/auth/authenticate" est l'autre chemin qui levait PatternParseException, et c'est aussi
    // le point d'entree qui appelle JwtUtil.generateToken -> la cle de signature faible aurait
    // fait echouer cette requete avec WeakKeyException avant le fix.
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    String token = json.get("accessToken").asText();
    assertNotNull(token);
    assertTrue(token.length() > 20);

    // Le token frais doit reellement autoriser l'acces a un endpoint protege.
    mockMvc.perform(get("/gestiondestock/v1/organizations/all")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  public void userWithoutEntrepriseCanStillLogIn() throws Exception {
    // Un Utilisateur peut exister sans Entreprise (UtilisateurValidator ne l'exige pas) :
    // ApplicationUserDetailsService/JwtUtil ne doivent pas planter dessus (NullPointerException
    // trouvee et corrigee lors de l'audit curl manuel).
    String email = "nolink-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    String createPayload = "{\"nom\":\"Sans Entreprise\",\"prenom\":\"Test\",\"email\":\"" + email + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
        + "\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\",\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}";

    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + bootstrapAdminToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createPayload))
        .andExpect(status().isOk());

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"Passw0rd!\"}";
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    assertNotNull(json.get("accessToken").asText());
  }

  /**
   * /utilisateurs/create est protege (authenticated() seul, pas de permission fine sur ce
   * endpoint legacy) : il faut un token valide quelconque pour l'appeler. On en cree un via une
   * Entreprise jetable plutot que de dupliquer la logique de login.
   */
  private String bootstrapAdminToken() throws Exception {
    String email = uniqueEmail();
    EntrepriseDto entreprise = EntrepriseDto.builder()
        .nom("Societe Bootstrap Token")
        .description("x")
        .codeFiscal("CF-BT-" + UUID.randomUUID().toString().substring(0, 8))
        .email(email)
        .numTel("+237600000011")
        .adresse(AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build();
    mockMvc.perform(post("/gestiondestock/v1/entreprises/create")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(entreprise)))
        .andExpect(status().isOk());

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"som3R@nd0mP@$$word\"}";
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
  }
}
