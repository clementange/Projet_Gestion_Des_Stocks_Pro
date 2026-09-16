package com.kfokam48.gestiondestock.tenant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 4b, etape 0 (nouveau flow) : /tenants/register remplace /entreprises/create et
 * generateRandomPassword() (mot de passe admin en dur, voir docs/phase-4b-report.md). Ces tests
 * couvrent specifiquement ce qui n'existait pas avant (le mot de passe vient desormais de
 * l'appelant) : inscription puis connexion immediate avec le mot de passe fourni, et la nouvelle
 * politique de validation (8 caracteres minimum, correspondance des deux champs).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class TenantRegistrationCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static AdresseDto adresse() {
    return AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build();
  }

  private TenantRegistrationRequest requestWithPassword(String email, String password, String confirmPassword) {
    return new TenantRegistrationRequest(
        "Societe Registration Test", "x", adresse(),
        uniqueCode("CF"), null, email, "+237600000099", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        password, confirmPassword);
  }

  @Test
  public void registrationSucceedsAndAdminCanLoginImmediatelyWithSuppliedPassword() throws Exception {
    String email = uniqueCode("register-ok") + "@test.local";
    String password = "Custom-Pass1";

    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestWithPassword(email, password, password))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").exists())
        .andReturn();
    org.junit.Assert.assertNotNull(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id"));

    // Preuve directe qu'aucun mot de passe genere par le serveur n'existe : seul le mot de passe
    // fourni dans la requete fonctionne.
    mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  public void registrationRejectsPasswordShorterThanEightCharacters() throws Exception {
    String email = uniqueCode("short-pwd") + "@test.local";

    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestWithPassword(email, "Sh0rt!", "Sh0rt!"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TENANT_REGISTRATION_NOT_VALID"));
  }

  @Test
  public void registrationRejectsMismatchedPasswordConfirmation() throws Exception {
    String email = uniqueCode("mismatch-pwd") + "@test.local";

    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestWithPassword(email, "Passw0rd-One", "Passw0rd-Two"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TENANT_REGISTRATION_NOT_VALID"));
  }

  @Test
  public void registrationRejectsDuplicateAdminEmail() throws Exception {
    String email = uniqueCode("dup-admin") + "@test.local";
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestWithPassword(email, "Passw0rd!1", "Passw0rd!1"))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(requestWithPassword(email, "Passw0rd!2", "Passw0rd!2"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
  }

  @Test
  public void registrationRejectsMissingRequiredFields() throws Exception {
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TENANT_REGISTRATION_NOT_VALID"));
  }
}
