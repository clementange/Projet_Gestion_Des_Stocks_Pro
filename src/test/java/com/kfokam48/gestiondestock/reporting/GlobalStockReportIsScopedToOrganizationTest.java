package com.kfokam48.gestiondestock.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 5g : {@code GET /reporting/stock/global} renvoyait le stock de TOUS les tenants.
 *
 * <p>{@code ReportingServiceImpl.getGlobalStockSummary} verifiait {@code REPORTING_VIEW} en portee
 * {@code GLOBAL} puis retournait {@code inventoryFacade.findAllStock()}, soit
 * {@code stockRepository.findAll()} — sans filtre d'organisation en SQL **ni** en Java. Tout admin
 * de tenant (qui possede REPORTING_VIEW/GLOBAL via l'amorcage ADMINISTRATEUR) lisait donc le stock
 * de ses concurrents : article, site, quantites.
 *
 * <p>La portee {@code GLOBAL} de la permission designe « toute mon organisation », pas « toutes
 * les organisations » — c'est exactement la confusion que {@code assignmentAppliesToScope}
 * entretient (une affectation GLOBAL matche n'importe quel {@code scopeId}, mais seulement DANS son
 * organisation). La matrice inter-tenant excluait cet endpoint avec la justification « filtre par
 * RBAC (5b-1) », qui etait fausse : le RBAC decidait du DROIT de lire, jamais du PERIMETRE lu.
 *
 * <p>Le marqueur de verification est le {@code codeArticle}, unique par tenant : chercher un id
 * dans le corps produirait des faux positifs (un id de site peut coincider avec un id d'article),
 * erreur deja commise et corrigee en 5b-2h.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class GlobalStockReportIsScopedToOrganizationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Test-Passw0rd!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  // Phase 5g : aucun endpoint n'ecrit seuilAlerte (verifie : aucun setter dans src/main), donc
  // /reporting/stock/low renvoie TOUJOURS une liste vide. Sans ce seuil pose directement en base,
  // l'assertion « le stock de B n'apparait pas » passerait sur un corps vide, c'est-a-dire pour
  // la mauvaise raison - voir docs/phase-5g-report.md §limites.
  @Autowired
  private com.kfokam48.gestiondestock.inventory.infrastructure.persistence.StockRepository stockRepository;

  private void markStockAsLow(long siteId) {
    stockRepository.findAllBySiteId(siteId).forEach(stock -> {
      stock.setSeuilAlerte(new java.math.BigDecimal("100"));
      stockRepository.save(stock);
    });
  }

  private Tenant a;
  private Tenant b;

  private static String uniq(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private record Tenant(String token, long organizationId, long siteId, String articleCode) {}

  @Before
  public void provisionTwoTenantsEachWithStock() throws Exception {
    a = tenantWithStock("glob-A");
    b = tenantWithStock("glob-B");
  }

  private Tenant tenantWithStock(String label) throws Exception {
    String email = uniq(label) + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe " + label, "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniq("CF"), null, email, "+237600000108", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"), PASSWORD, PASSWORD);
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());

    String login = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String token = objectMapper.readTree(login).get("accessToken").asText();
    long organizationId = objectMapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]))
        .get("organizationId").asLong();

    long cityId = idOf(ok(post("/gestiondestock/v1/cities/create")
        .content("{\"name\":\"Douala\",\"organization\":{\"id\":" + organizationId + "}}"), token));
    long siteId = idOf(ok(post("/gestiondestock/v1/sites/create").content(
        "{\"code\":\"" + uniq("S") + "\",\"name\":\"Entrepot\",\"type\":\"ENTREPOT\",\"active\":true,"
            + "\"city\":{\"id\":" + cityId + "}}"), token));
    long categoryId = idOf(ok(post("/gestiondestock/v1/categories/create")
        .content("{\"code\":\"" + uniq("C") + "\",\"designation\":\"Cat\"}"), token));
    String articleCode = uniq("ART-" + label);
    long articleId = idOf(ok(post("/gestiondestock/v1/articles/create").content(
        "{\"codeArticle\":\"" + articleCode + "\",\"designation\":\"Article\",\"prixUnitaireHt\":100,"
            + "\"tauxTva\":19.25,\"prixUnitaireTtc\":119.25,\"category\":{\"id\":" + categoryId
            + "}}"), token));

    // Du stock reel, avec un seuil d'alerte franchi pour que la ligne apparaisse aussi dans le
    // rapport de rupture (/reporting/stock/low), couvert par le meme test.
    ok(post("/gestiondestock/v1/stocks/article/" + articleId + "/site/" + siteId
        + "/entree?quantite=5&reference=INIT"), token);

    return new Tenant(token, organizationId, siteId, articleCode);
  }

  @Test
  public void globalStockReportMustNotExposeAnotherTenantStock() throws Exception {
    String body = ok(get("/gestiondestock/v1/reporting/stock/global"), a.token());

    assertTrue("l'admin de A doit voir SON propre stock", body.contains(a.articleCode()));
    assertFalse("le stock du tenant B ne doit jamais apparaitre dans le rapport global de A : "
            + "la portee GLOBAL de REPORTING_VIEW signifie « toute mon organisation », pas "
            + "« toutes les organisations ». Corps recu : " + body,
        body.contains(b.articleCode()));
  }

  @Test
  public void everyLineOfTheGlobalReportBelongsToTheCallerOrganization() throws Exception {
    JsonNode lines = objectMapper.readTree(ok(get("/gestiondestock/v1/reporting/stock/global"), a.token()));

    assertTrue("le rapport ne doit pas etre vide, sinon ce test passerait pour la mauvaise raison",
        lines.size() > 0);
    for (JsonNode line : lines) {
      assertEquals("chaque ligne doit porter un site de l'organisation de l'appelant",
          a.siteId(), line.get("site").get("id").asLong());
    }
  }

  @Test
  public void lowStockReportMustNotExposeAnotherTenantStock() throws Exception {
    markStockAsLow(a.siteId());
    markStockAsLow(b.siteId());

    String body = ok(get("/gestiondestock/v1/reporting/stock/low"), a.token());

    assertTrue("A doit voir sa propre rupture, sinon ce test passerait sur un corps vide",
        body.contains(a.articleCode()));
    assertFalse("le stock du tenant B ne doit jamais apparaitre dans le rapport de rupture de A. "
            + "Corps recu : " + body,
        body.contains(b.articleCode()));
  }

  private String ok(MockHttpServletRequestBuilder request, String token) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private long idOf(String json) throws Exception {
    return objectMapper.readTree(json).get("id").asLong();
  }
}
