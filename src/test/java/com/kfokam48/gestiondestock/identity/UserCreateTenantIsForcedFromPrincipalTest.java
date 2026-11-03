package com.kfokam48.gestiondestock.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.shared.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
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

/**
 * Backlog du Lot 1, gravite elevee : {@code POST /users/create} acceptait
 * {@code idEntreprise} depuis le CORPS de la requete. L'admin du tenant B pouvait donc creer un
 * utilisateur — avec le mot de passe de son choix — directement DANS le tenant A, et ce compte,
 * une fois authentifie, resolvait l'{@code organizationId} de A : une primitive d'entree
 * permanente dans le tenant d'autrui.
 *
 * <p>Pourquoi la matrice inter-tenant de 5b-2h ne l'a pas vu : elle sonde l'{@code id} de la
 * RESSOURCE visee, jamais la valeur d'un CHAMP du corps. Et {@code hasPermission(USER_CREATE)}
 * passe legitimement — B a bien le droit de creer des utilisateurs, simplement pas chez A.
 *
 * <p>Correctif : {@code idEntreprise} est desormais impose depuis le principal et la valeur du
 * corps est ignoree. Meme choix que {@code /cities/filter/organization/{id}} en 5b-2h (seul le
 * principal fait foi) plutot qu'un rejet explicite a la {@code CREATION_ID_FORBIDDEN} : envoyer
 * son PROPRE tenant est un usage legitime et repandu, rejeter toute valeur casserait des appelants
 * corrects pour aucun gain.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class UserCreateTenantIsForcedFromPrincipalTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Test-Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniq(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private record Tenant(String token, long tenantId, long organizationId) {}

  private Tenant register(String label) throws Exception {
    String email = uniq(label) + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe " + label, "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000099", null,
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
        claims.get("organizationId").asLong());
  }

  private String createUser(String callerToken, String email, Long idEntrepriseInBody) throws Exception {
    return mockMvc.perform(post("/gestiondestock/v1/users/create")
            .header("Authorization", "Bearer " + callerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Intrus\",\"prenom\":\"Cross\",\"email\":\"" + email + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + PASSWORD + "\","
                + (idEntrepriseInBody == null ? "" : "\"idEntreprise\":" + idEntrepriseInBody + ",")
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  public void bCannotPlantAUserInsideTenantAByPassingItsIdEntreprise() throws Exception {
    Tenant a = register("cross-A");
    Tenant b = register("cross-B");

    // B cree un utilisateur en REVENDIQUANT le tenant de A dans le corps.
    String intruderEmail = uniq("intrus") + "@test.local";
    String created = createUser(b.token(), intruderEmail, a.tenantId());

    // Le tenant du corps doit avoir ete ignore : l'utilisateur appartient a B, jamais a A.
    assertEquals("idEntreprise doit venir du principal, pas du corps de la requete",
        b.tenantId(), objectMapper.readTree(created).get("idEntreprise").asLong());

    // Preuve cote A : son administrateur ne voit pas cet utilisateur dans SON tenant.
    String usersOfA = mockMvc.perform(get("/gestiondestock/v1/users/all")
            .header("Authorization", "Bearer " + a.token()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertFalse("l'utilisateur plante par B ne doit pas apparaitre dans le tenant de A",
        usersOfA.contains(intruderEmail));

    // Et le compte, authentifie, resout l'organisation de B — pas celle de A.
    String login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + intruderEmail + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String intruderToken = objectMapper.readTree(login).get("accessToken").asText();
    JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(intruderToken.split("\\.")[1]));
    assertEquals("le compte cree par B ne doit jamais resoudre l'organisation de A",
        b.organizationId(), claims.get("organizationId").asLong());
  }

  @Test
  public void aUserCreatedWithoutIdEntrepriseLandsInTheCallerTenant() throws Exception {
    Tenant a = register("own-tenant");

    String email = uniq("membre") + "@test.local";
    String created = createUser(a.token(), email, null);

    // Corps sans idEntreprise : l'utilisateur rejoint le tenant de l'appelant, il n'est plus
    // orphelin. Avant ce correctif il n'appartenait a AUCUN tenant et devenait invisible aux
    // lectures scopees - un compte fantome cree par inadvertance.
    assertEquals(a.tenantId(), objectMapper.readTree(created).get("idEntreprise").asLong());
  }
}
