package com.kfokam48.gestiondestock.legacy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Couverture HTTP reelle (MockMvc, vrai filtre de securite) des controleurs legacy qui n'avaient
 * jusqu'ici AUCUN test dedie : Client, Fournisseur, Entreprise, Utilisateur, CommandeClient,
 * CommandeFournisseur, Ventes, MvtStk. Ces controleurs n'etaient exerces que par des appels de
 * service Java directs dans le reste de la suite, ce qui masquait plusieurs bugs reels trouves
 * lors de l'audit curl manuel (Phase 15) :
 * <ul>
 *   <li>CommandeClientApi.findById : @PathVariable sans nom explicite, 500 "Required path
 *   variable 'id' is not present" ;</li>
 *   <li>VentesApi.findByCode : collision de route avec findById (meme forme "/{x}"), rendait
 *   l'un des deux endpoints inatteignable ;</li>
 *   <li>VentesDto.toEntity/fromEntity : bug de copier-coller (ventes.setCode(ventes.getCode())
 *   au lieu de dto.getCode()) qui perdait code et dateVente sur CHAQUE vente creee ;</li>
 *   <li>CommandeClientValidator/CommandeFournisseurValidator : NullPointerException sur
 *   etatCommande null au lieu d'un message de validation propre.</li>
 * </ul>
 * Chaque test ci-dessous couvre precisement l'un de ces bugs en plus du flux nominal, pour
 * qu'une regression future soit detectee automatiquement plutot que seulement par un futur audit
 * manuel.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class LegacyControllersIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private com.kfokam48.gestiondestock.repository.EntrepriseRepository entrepriseRepository;

  @Autowired
  private com.kfokam48.gestiondestock.organization.application.OrganizationService organizationService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("legacy-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Legacy Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000020", null,
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

  private Long createArticleForTests(String token) throws Exception {
    String catCode = uniqueCode("CAT");
    mockMvc.perform(post("/gestiondestock/v1/categories/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"" + catCode + "\",\"designation\":\"Cat test\"}"))
        .andExpect(status().isOk());
    JsonNode cat = objectMapper.readTree(mockMvc.perform(get("/gestiondestock/v1/categories/filter/" + catCode)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    long catId = cat.get("id").asLong();

    String artCode = uniqueCode("ART");
    MvcResult articleResult = mockMvc.perform(post("/gestiondestock/v1/articles/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"codeArticle\":\"" + artCode + "\",\"designation\":\"Article test\",\"prixUnitaireHt\":100,"
                + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + catId + "}}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(articleResult.getResponse().getContentAsString()).get("id").asLong();
  }

  @Test
  public void clientCrudFlowWorksOverHttp() throws Exception {
    String token = adminToken();
    String payload = "{\"nom\":\"Client HTTP\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("client") + "@test.local\","
        + "\"numTel\":\"600000030\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";

    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/clients/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nom").value("Client HTTP"));

    mockMvc.perform(delete("/gestiondestock/v1/clients/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/clients/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  public void fournisseurCrudFlowWorksOverHttp() throws Exception {
    String token = adminToken();
    String payload = "{\"nom\":\"Fournisseur HTTP\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("fourn") + "@test.local\","
        + "\"numTel\":\"600000031\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";

    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/fournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/fournisseurs/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nom").value("Fournisseur HTTP"));
  }

  @Test
  public void utilisateurCrudFlowAndFindByEmailWorkOverHttp() throws Exception {
    String token = adminToken();
    String email = uniqueCode("util") + "@test.local";
    String payload = "{\"nom\":\"Util HTTP\",\"prenom\":\"Test\",\"email\":\"" + email + "\","
        + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"moteDePasse\":\"Passw0rd!\","
        + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}";

    mockMvc.perform(post("/gestiondestock/v1/utilisateurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/utilisateurs/find/" + email).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  public void commandeClientFindByIdWorksOverHttp() throws Exception {
    // Regression : CommandeClientApi.findById(@PathVariable Long idCommandeClient) sans nom
    // explicite renvoyait 500 "Required path variable 'id' is not present" (le parametre de la
    // classe d'implementation s'appelle "id", pas "idCommandeClient").
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    MvcResult clientResult = mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"C\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("cli") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long clientId = objectMapper.readTree(clientResult.getResponse().getContentAsString()).get("id").asLong();

    String code = uniqueCode("CMD");
    String payload = "{\"code\":\"" + code + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"client\":{\"id\":" + clientId + "},\"ligneCommandeClients\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":2,\"prixUnitaire\":10}]}";

    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/commandesclients/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  public void commandeClientCreationWithoutEtatCommandeReturnsCleanValidationErrorNotCrash() throws Exception {
    // Regression : CommandeClientValidator faisait dto.getEtatCommande().toString() sans garde
    // null -> NullPointerException au lieu d'un message de validation, quand un client (a juste
    // titre) ne precise pas d'etat a la creation.
    String token = adminToken();
    String payload = "{\"code\":\"" + uniqueCode("CMD") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"client\":{\"id\":1}}";

    mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_CLIENT_NOT_VALID"))
        .andExpect(jsonPath("$.errors[0]").value("Veuillez renseigner l'etat de la commande"));
  }

  @Test
  public void commandeFournisseurCreationWithoutEtatCommandeReturnsCleanValidationErrorNotCrash() throws Exception {
    String token = adminToken();
    String payload = "{\"code\":\"" + uniqueCode("CMF") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\","
        + "\"fournisseur\":{\"id\":1}}";

    mockMvc.perform(post("/gestiondestock/v1/commandesfournisseurs/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMANDE_FOURNISSEUR_NOT_VALID"));
  }

  @Test
  public void ventesCreateFindByCodeRoundTripPreservesCodeAndDate() throws Exception {
    // Regression : VentesDto.toEntity faisait ventes.setCode(ventes.getCode()) (copie de
    // l'entite neuve sur elle-meme, toujours null) au lieu de dto.getCode() -> code et
    // dateVente perdus sur chaque vente creee. VentesApi.findByCode utilisait en plus
    // "/{codeVente}", strictement le meme motif que findById "/{idVente}" -> route inatteignable.
    // Phase 19 : /ventes/create est desormais re-backe par sales.Sale, qui exige au moins une
    // ligne et un stock physique suffisant sur le site vendeur (invariants absents du legacy) ->
    // une ligne est desormais fournie, et le stock du site par defaut est approvisionne au
    // prealable via l'endpoint /stocks du module inventory.
    String email = uniqueCode("legacy-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Vente Test " + uniqueCode(""), "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000021", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk())
        .andReturn();
    Long entrepriseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    String loginPayload = "{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}";
    MvcResult loginResult = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(loginPayload))
        .andExpect(status().isOk())
        .andReturn();
    String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

    Long articleId = createArticleForTests(token);

    Long organizationId = entrepriseRepository.findById(entrepriseId).orElseThrow().getOrganizationId();
    Long siteId = organizationService.ensureDefaultSite(organizationId).getId();
    mockMvc.perform(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId + "/entree?quantite=10&reference=seed-test")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    String code = uniqueCode("VEN");
    String payload = "{\"code\":\"" + code + "\",\"dateVente\":\"2026-09-11T00:00:00Z\",\"commentaire\":\"test\","
        + "\"ligneVentes\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";

    mockMvc.perform(post("/gestiondestock/v1/ventes/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.dateVente").exists());

    mockMvc.perform(get("/gestiondestock/v1/ventes/filter/" + code).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  public void mvtStkEntreeAndStockReelWorkOverHttp() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);

    String payload = "{\"article\":{\"id\":" + articleId + "},\"dateMvt\":\"2026-09-11T00:00:00Z\",\"quantite\":15,"
        + "\"typeMvt\":\"ENTREE\",\"sourceMvt\":\"COMMANDE_FOURNISSEUR\"}";
    mockMvc.perform(post("/gestiondestock/v1/mvtstk/entree")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/mvtstk/stockreel/" + articleId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  public void commandeClientUpdateEtatCommandeWorksOverHttp() throws Exception {
    String token = adminToken();
    Long articleId = createArticleForTests(token);
    MvcResult clientResult = mockMvc.perform(post("/gestiondestock/v1/clients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"C\",\"prenom\":\"P\",\"mail\":\"" + uniqueCode("cli") + "@test.local\",\"numTel\":\"6\","
                + "\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long clientId = objectMapper.readTree(clientResult.getResponse().getContentAsString()).get("id").asLong();

    String payload = "{\"code\":\"" + uniqueCode("CMD") + "\",\"dateCommande\":\"2026-09-11T00:00:00Z\",\"etatCommande\":\"EN_PREPARATION\","
        + "\"client\":{\"id\":" + clientId + "},\"ligneCommandeClients\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":10}]}";
    MvcResult created = mockMvc.perform(post("/gestiondestock/v1/commandesclients/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(patch("/gestiondestock/v1/commandesclients/update/etat/" + id + "/VALIDEE")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.etatCommande").value("VALIDEE"));
  }
}
