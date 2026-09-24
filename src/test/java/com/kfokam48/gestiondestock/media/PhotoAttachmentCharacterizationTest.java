package com.kfokam48.gestiondestock.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 4c, etape 0 (mecanisme neuf, aucune couverture prealable - voir docs/phase-4c-report.md) :
 * {@code POST /media/upload} (MinIO, remplace Flickr/StrategyPhotoContext) puis attachement de
 * l'URL via {@code POST /{ressource}/{id}/photo} sur chacun des 5 types d'entite concernes.
 *
 * <p>{@link #attachingPhotoToUserDoesNotCorruptPasswordOrRejectAsDuplicate()} est le test le plus
 * important du fichier : il reproduit exactement le bug pre-existant trouve en investiguant
 * (UserServiceImpl.save() re-hachait un mot de passe deja hache et rejetait systematiquement en
 * doublon sur toute mise a jour via l'ancien SaveUtilisateurPhoto) et prouve qu'il n'existe plus
 * avec updatePhoto().
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class PhotoAttachmentCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken(String email, String password) throws Exception {
    String payload = "{"
        + "\"nom\":\"Societe Photo Test\",\"description\":\"x\","
        + "\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\",\"pays\":\"Cameroun\",\"codePostale\":\"00000\"},"
        + "\"codeFiscal\":\"" + uniqueCode("CF") + "\",\"email\":\"" + email + "\",\"numTel\":\"+237600000099\","
        + "\"adminNom\":\"Admin\",\"adminPrenom\":\"Photo\",\"adminEmail\":\"" + email + "\","
        + "\"adminDateDeNaissance\":\"1990-01-01T00:00:00Z\","
        + "\"motDePasse\":\"" + password + "\",\"confirmMotDePasse\":\"" + password + "\"}";
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());
    MvcResult login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private String uploadMedia(String token) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "fake-image-bytes".getBytes());
    MvcResult result = mockMvc.perform(multipart("/gestiondestock/v1/media/upload")
            .file(file)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("url").asText();
  }

  @Test
  public void uploadReturnsAPubliclyReadableUrl() throws Exception {
    String token = adminToken(uniqueCode("upload") + "@test.local", "Test-Passw0rd!");
    String url = uploadMedia(token);

    assertNotNull(url);
    assertTrue(url.startsWith("http"));

    HttpResponse<Void> response = HttpClient.newHttpClient()
        .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.discarding());
    assertEquals(200, response.statusCode());
  }

  @Test
  public void attachingPhotoToArticleUpdatesItsPhotoField() throws Exception {
    String token = adminToken(uniqueCode("article-photo") + "@test.local", "Test-Passw0rd!");
    String catCode = uniqueCode("CAT");
    mockMvc.perform(post("/gestiondestock/v1/categories/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + catCode + "\",\"designation\":\"Cat test\"}"))
        .andExpect(status().isOk());
    JsonNode cat = objectMapper.readTree(mockMvc.perform(get("/gestiondestock/v1/categories/filter/" + catCode)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

    MvcResult articleResult = mockMvc.perform(post("/gestiondestock/v1/articles/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"codeArticle\":\"" + uniqueCode("ART") + "\",\"designation\":\"Article test\",\"prixUnitaireHt\":100,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + cat.get("id").asLong() + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    long articleId = objectMapper.readTree(articleResult.getResponse().getContentAsString()).get("id").asLong();

    String url = uploadMedia(token);

    mockMvc.perform(post("/gestiondestock/v1/articles/" + articleId + "/photo")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));

    mockMvc.perform(get("/gestiondestock/v1/articles/" + articleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));
  }

  @Test
  public void attachingPhotoToClientUpdatesItsPhotoField() throws Exception {
    String token = adminToken(uniqueCode("client-photo") + "@test.local", "Test-Passw0rd!");
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/customers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Client Photo\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("cli") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long clientId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    String url = uploadMedia(token);

    mockMvc.perform(post("/gestiondestock/v1/clients/" + clientId + "/photo")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));
  }

  @Test
  public void attachingPhotoToFournisseurUpdatesItsPhotoField() throws Exception {
    String token = adminToken(uniqueCode("fourn-photo") + "@test.local", "Test-Passw0rd!");
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/suppliers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Fournisseur Photo\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("fourn") + "@test.local\","
                + "\"numTel\":\"600000031\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long fournisseurId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    String url = uploadMedia(token);

    mockMvc.perform(post("/gestiondestock/v1/fournisseurs/" + fournisseurId + "/photo")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));
  }

  @Test
  public void attachingPhotoToTenantUpdatesItsPhotoFieldWithoutRerunningRegistration() throws Exception {
    String email = uniqueCode("tenant-photo") + "@test.local";
    String token = adminToken(email, "Test-Passw0rd!");

    MvcResult tenantResult = mockMvc.perform(get("/gestiondestock/v1/users/find/" + email)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    long idEntreprise = objectMapper.readTree(tenantResult.getResponse().getContentAsString()).get("idEntreprise").asLong();

    String url = uploadMedia(token);

    mockMvc.perform(post("/gestiondestock/v1/tenants/" + idEntreprise + "/photo")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));
  }

  @Test
  public void attachingPhotoToUserDoesNotCorruptPasswordOrRejectAsDuplicate() throws Exception {
    String email = uniqueCode("user-photo") + "@test.local";
    String password = "Test-Passw0rd!";
    String token = adminToken(email, password);

    MvcResult meResult = mockMvc.perform(get("/gestiondestock/v1/users/find/" + email)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    long userId = objectMapper.readTree(meResult.getResponse().getContentAsString()).get("id").asLong();

    String url = uploadMedia(token);

    // Avant Phase 4c, ce meme flux (findById -> setPhoto -> save()) re-hachait le mot de passe
    // deja hache ET rejetait systematiquement en USER_ALREADY_EXISTS - voir docs/phase-4c-report.md.
    mockMvc.perform(post("/gestiondestock/v1/users/" + userId + "/photo")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.photo").value(url));

    // Preuve directe que le mot de passe n'a pas ete corrompu : le login avec le mot de passe
    // ORIGINAL (fourni a l'inscription) fonctionne toujours apres l'upload de photo.
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  /**
   * Phase 5b-2c : {@code POST /users/{id}/photo} n'avait avant ce correctif aucune verification
   * de propriete (n'importe quel utilisateur authentifie pouvait definir la photo de n'importe
   * quel autre) - meme classe d'IDOR que changePassword, corrige en self-only avec le meme motif
   * (mirror de {@code UtilisateurAuthenticationCharacterizationTest.changerMotDePasseRejectsWhenCallerIsNotTargetUser})
   * - voir docs/phase-5b2c-report.md.
   */
  @Test
  public void attachingPhotoToAnotherUserIsRejected() throws Exception {
    String adminEmail = uniqueCode("photo-victim") + "@test.local";
    String adminToken = adminToken(adminEmail, "Test-Passw0rd!");
    MvcResult meResult = mockMvc.perform(get("/gestiondestock/v1/users/find/" + adminEmail)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andReturn();
    long victimId = objectMapper.readTree(meResult.getResponse().getContentAsString()).get("id").asLong();

    String attackerEmail = uniqueCode("photo-attacker") + "@test.local";
    String attackerPassword = "Passw0rd!";
    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Attacker\",\"prenom\":\"User\",\"email\":\"" + attackerEmail + "\","
                + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"" + attackerPassword + "\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk());
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + attackerEmail + "\",\"password\":\"" + attackerPassword + "\"}"))
        .andExpect(status().isOk()).andReturn();
    String attackerToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

    String url = uploadMedia(adminToken);

    mockMvc.perform(post("/gestiondestock/v1/users/" + victimId + "/photo")
            .header("Authorization", "Bearer " + attackerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_UPDATE_PHOTO_FORBIDDEN"));
  }
}
