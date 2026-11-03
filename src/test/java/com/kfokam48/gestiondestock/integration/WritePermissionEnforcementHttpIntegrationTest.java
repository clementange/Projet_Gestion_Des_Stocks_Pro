package com.kfokam48.gestiondestock.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 5c : l'appartenance au tenant ne suffit plus, il faut le DROIT.
 *
 * <p>Deux utilisateurs du MEME tenant : l'admin issu de l'amorcage (role ADMINISTRATEUR, donc
 * toutes les permissions de V8) et un simple membre sans aucune affectation de role. Le membre
 * doit etre refuse sur chaque ecriture avec un {@code *_ACCESS_DENIED}, alors que l'admin reussit.
 *
 * <p>Le code d'erreur attendu est important : un refus d'APPARTENANCE renvoie un
 * {@code *_NOT_FOUND} masque (5b-2*), un refus de PERMISSION renvoie {@code *_ACCESS_DENIED}. Voir
 * un {@code *_ACCESS_DENIED} ici prouve que l'appartenance est passee (le membre est bien dans
 * l'organisation) et que c'est bien la permission qui a bloque - donc que l'ordre
 * « appartenance -> permission -> ecriture » est respecte, et non l'inverse.
 *
 * <p>Avant cet increment, toutes ces requetes renvoyaient 200 : tout membre d'une organisation
 * pouvait y creer, modifier et supprimer n'importe quoi - voir docs/phase-5c-report.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class WritePermissionEnforcementHttpIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Test-Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private String adminToken;
  private String memberToken;
  private long orgId;
  private long cityId;
  private long siteId;
  private long otherSiteId;
  private long categoryId;
  private long articleId;
  private long customerId;
  private long supplierId;
  private long warehouseId;
  private long memberUserId;

  private static String uniq(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @BeforeEach
  public void provisionOneTenantWithAnAdminAndAPermissionlessMember() throws Exception {
    String adminEmail = uniq("5c-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe 5c", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, adminEmail, "+237600000090", null,
        "Admin", "Test", adminEmail, Instant.parse("1990-01-01T00:00:00Z"), PASSWORD, PASSWORD);
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    adminToken = authenticate(adminEmail);
    orgId = claim(adminToken, "organizationId");

    // Le tenant de l'admin, lu depuis la ressource et non devine : le membre doit etre cree dans
    // CE tenant, sinon il serait d'une autre organisation et ce test prouverait l'appartenance au
    // lieu de la permission.
    long tenantId = -1;
    JsonNode users = objectMapper.readTree(okBody(get("/gestiondestock/v1/users/all"), adminToken));
    for (JsonNode user : users) {
      if (adminEmail.equals(user.get("email").asText())) {
        tenantId = user.get("idEntreprise").asLong();
      }
    }
    org.junit.jupiter.api.Assertions.assertNotEquals(-1, tenantId, "Le tenant de l'admin doit etre lisible");

    String memberEmail = uniq("5c-member") + "@test.local";
    memberUserId = idOf(okBody(post("/gestiondestock/v1/users/create").content(
        "{\"nom\":\"Membre\",\"prenom\":\"Sans\",\"email\":\"" + memberEmail + "\","
            + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + PASSWORD + "\","
            + "\"idEntreprise\":" + tenantId + ",\"adresse\":{\"adresse1\":\"1 Rue\","
            + "\"ville\":\"Douala\",\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), adminToken));
    memberToken = authenticate(memberEmail);
    org.junit.jupiter.api.Assertions.assertEquals(orgId, claim(memberToken, "organizationId"),
        "Le membre doit etre dans la MEME organisation que l'admin, sinon ce test mesurerait "
            + "l'appartenance et non la permission");

    // Jeu de ressources cree par l'admin : prouve au passage que l'admin, lui, reussit.
    cityId = idOf(okBody(post("/gestiondestock/v1/cities/create")
        .content("{\"name\":\"Douala\",\"organization\":{\"id\":" + orgId + "}}"), adminToken));
    siteId = idOf(okBody(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\","
            + "\"active\":true,\"city\":{\"id\":" + cityId + "}}"), adminToken));
    otherSiteId = idOf(okBody(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S2") + "\",\"name\":\"Entrepot 2\",\"type\":\"ENTREPOT\","
            + "\"active\":true,\"city\":{\"id\":" + cityId + "}}"), adminToken));
    categoryId = idOf(okBody(post("/gestiondestock/v1/categories/create")
        .content("{\"code\":\"" + uniq("C") + "\",\"designation\":\"Cat\"}"), adminToken));
    articleId = idOf(okBody(post("/gestiondestock/v1/articles/create").content(
        "{\"codeArticle\":\"" + uniq("A") + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + categoryId
            + "}}"), adminToken));
    customerId = idOf(okBody(post("/gestiondestock/v1/customers/create").content(
        "{\"nom\":\"Client\",\"prenom\":\"Un\",\"email\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000091\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), adminToken));
    supplierId = idOf(okBody(post("/gestiondestock/v1/suppliers/create").content(
        "{\"nom\":\"Fournisseur\",\"prenom\":\"Un\",\"email\":\"" + uniq("four") + "@test.local\","
            + "\"numTel\":\"+237600000092\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), adminToken));
    warehouseId = idOf(okBody(post("/gestiondestock/v1/warehouses/create").content(
        "{\"code\":\"" + uniq("W") + "\",\"name\":\"Magasin\",\"site\":{\"id\":" + siteId + "}}"),
        adminToken));
  }

  // ---------------------------------------------------------------- creations

  @Test
  public void aMemberWithoutPermissionCannotCreateAnyOrganizationScopedResource() throws Exception {
    expectDenied(post("/gestiondestock/v1/categories/create")
        .content("{\"code\":\"" + uniq("C") + "\",\"designation\":\"Cat\"}"), "CATEGORY_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/articles/create").content(
        "{\"codeArticle\":\"" + uniq("A") + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + categoryId
            + "}}"), "ARTICLE_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/cities/create")
        .content("{\"name\":\"Yaounde\",\"organization\":{\"id\":" + orgId + "}}"), "CITY_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/customers/create").content(
        "{\"nom\":\"Client\",\"prenom\":\"Deux\",\"email\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000093\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), "CUSTOMER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/suppliers/create").content(
        "{\"nom\":\"Fournisseur\",\"prenom\":\"Deux\",\"email\":\"" + uniq("four") + "@test.local\","
            + "\"numTel\":\"+237600000094\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), "SUPPLIER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/warehouses/create")
        .content("{\"code\":\"" + uniq("W") + "\",\"name\":\"Magasin\",\"site\":{\"id\":" + siteId + "}}"),
        "WAREHOUSE_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\","
            + "\"active\":true,\"city\":{\"id\":" + cityId + "}}"), "SITE_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/users/create").content(
        "{\"nom\":\"Autre\",\"prenom\":\"Membre\",\"email\":\"" + uniq("x") + "@test.local\","
            + "\"dateDeNaissance\":\"1990-01-01T00:00:00Z\",\"motDePasse\":\"" + PASSWORD + "\","
            + "\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\",\"pays\":\"Cameroun\","
            + "\"codePostale\":\"00000\"}}"), "USER_ACCESS_DENIED");
  }

  @Test
  public void aMemberWithoutPermissionCannotCreateAnyAggregate() throws Exception {
    expectDenied(post("/gestiondestock/v1/customer-orders/create").content(
        "{\"order\":{\"code\":\"" + uniq("CC") + "\",\"site\":{\"id\":" + siteId + "},"
            + "\"customerId\":" + customerId + "},"
            + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":100}]}"),
        "CUSTOMER_ORDER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/purchase-orders/create").content(
        "{\"order\":{\"code\":\"" + uniq("CF") + "\",\"site\":{\"id\":" + siteId + "},"
            + "\"supplierId\":" + supplierId + "},"
            + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantiteCommandee\":1,"
            + "\"prixUnitaire\":100}]}"), "PURCHASE_ORDER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/stock-transfers/create").content(
        "{\"transfer\":{\"code\":\"" + uniq("TR") + "\",\"originSite\":{\"id\":" + siteId + "},"
            + "\"destinationSite\":{\"id\":" + otherSiteId + "},\"requestedByUserId\":1},"
            + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1}]}"),
        "STOCK_TRANSFER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId
        + "/entree?quantite=5&reference=REF"), "STOCK_ACCESS_DENIED");
  }

  // ------------------------------------------------------------ updates/deletes

  @Test
  public void aMemberWithoutPermissionCannotUpdateAnOwnedResource() throws Exception {
    expectDenied(put("/gestiondestock/v1/articles/update/" + articleId).content(
        "{\"codeArticle\":\"" + uniq("A") + "\",\"designation\":\"Pirate\",\"prixUnitaireHt\":1,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":1.19,\"category\":{\"id\":" + categoryId + "}}"),
        "ARTICLE_ACCESS_DENIED");
    expectDenied(put("/gestiondestock/v1/categories/update/" + categoryId)
        .content("{\"code\":\"" + uniq("C") + "\",\"designation\":\"Pirate\"}"), "CATEGORY_ACCESS_DENIED");
    expectDenied(put("/gestiondestock/v1/cities/update/" + cityId)
        .content("{\"name\":\"Pirate\"}"), "CITY_ACCESS_DENIED");
    expectDenied(put("/gestiondestock/v1/warehouses/update/" + warehouseId)
        .content("{\"code\":\"" + uniq("W") + "\",\"name\":\"Pirate\",\"site\":{\"id\":" + siteId + "}}"),
        "WAREHOUSE_ACCESS_DENIED");
    expectDenied(put("/gestiondestock/v1/customers/update/" + customerId).content(
        "{\"nom\":\"Pirate\",\"prenom\":\"Un\",\"email\":\"" + uniq("cli") + "@test.local\","
            + "\"numTel\":\"+237600000095\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), "CUSTOMER_ACCESS_DENIED");
    expectDenied(put("/gestiondestock/v1/suppliers/update/" + supplierId).content(
        "{\"nom\":\"Pirate\",\"prenom\":\"Un\",\"email\":\"" + uniq("four") + "@test.local\","
            + "\"numTel\":\"+237600000096\",\"adresse\":{\"adresse1\":\"1 Rue\",\"ville\":\"Douala\","
            + "\"pays\":\"Cameroun\",\"codePostale\":\"00000\"}}"), "SUPPLIER_ACCESS_DENIED");

    // Et la ressource n'a pas bouge.
    mockMvc.perform(get("/gestiondestock/v1/categories/" + categoryId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.designation").value("Cat"));
  }

  @Test
  public void aMemberWithoutPermissionCannotDeleteAnOwnedResource() throws Exception {
    expectDenied(delete("/gestiondestock/v1/warehouses/delete/" + warehouseId), "WAREHOUSE_ACCESS_DENIED");
    expectDenied(delete("/gestiondestock/v1/articles/delete/" + articleId), "ARTICLE_ACCESS_DENIED");
    expectDenied(delete("/gestiondestock/v1/customers/delete/" + customerId), "CUSTOMER_ACCESS_DENIED");
    expectDenied(delete("/gestiondestock/v1/suppliers/delete/" + supplierId), "SUPPLIER_ACCESS_DENIED");
    expectDenied(delete("/gestiondestock/v1/users/delete/" + memberUserId), "USER_ACCESS_DENIED");

    // Les ressources sont toujours la.
    mockMvc.perform(get("/gestiondestock/v1/articles/" + articleId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
    mockMvc.perform(get("/gestiondestock/v1/users/" + memberUserId)
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  @Test
  public void aMemberWithoutPermissionCannotDriveAnAggregateStateMachine() throws Exception {
    long orderId = idOf(okBody(post("/gestiondestock/v1/customer-orders/create").content(
        "{\"order\":{\"code\":\"" + uniq("CC") + "\",\"site\":{\"id\":" + siteId + "},"
            + "\"customerId\":" + customerId + "},"
            + "\"lines\":[{\"article\":{\"id\":" + articleId + "},\"quantite\":1,\"prixUnitaire\":100}]}"),
        adminToken));

    // Les transitions deja gardees en 5b-2h l'etaient par l'appartenance seule : un membre du
    // meme tenant passait. Phase 5c y ajoute la permission.
    expectDenied(post("/gestiondestock/v1/customer-orders/" + orderId + "/valider"),
        "CUSTOMER_ORDER_ACCESS_DENIED");
    expectDenied(post("/gestiondestock/v1/customer-orders/" + orderId + "/annuler"),
        "CUSTOMER_ORDER_ACCESS_DENIED");

    // L'admin, lui, peut toujours valider : la permission existe et est accordee par V8.
    mockMvc.perform(post("/gestiondestock/v1/customer-orders/" + orderId + "/valider")
            .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  // ---------------------------------------------------------------- helpers

  private void expectDenied(MockHttpServletRequestBuilder request, String expectedCode) throws Exception {
    mockMvc.perform(request.header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(expectedCode));
  }

  private String okBody(MockHttpServletRequestBuilder request, String token) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private String authenticate(String email) throws Exception {
    String body = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("accessToken").asText();
  }

  private long claim(String token, String name) throws Exception {
    return objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]))
        .get(name).asLong();
  }

  private long idOf(String json) throws Exception {
    return objectMapper.readTree(json).get("id").asLong();
  }
}
