package com.kfokam48.gestiondestock.legacy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import com.kfokam48.gestiondestock.dto.AdresseDto;
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

/**
 * Phase 4d, etape 0 (contrat neuf, aucune couverture prealable - voir docs/phase-4d-report.md) :
 * {@code CustomerController}/{@code SupplierController} n'existaient pas avant cet increment ;
 * {@code sales.Customer}/{@code purchasing.Supplier} n'etaient exposes que via les adaptateurs
 * legacy {@code /clients/*}/{@code /fournisseurs/*}, supprimes dans ce meme increment. Cette classe
 * verifie uniquement le branchement HTTP (routage, binding JSON, codes d'erreur) : la logique
 * metier elle-meme (validation, garde-fou de suppression) est deja testee au niveau service par
 * CustomerServiceImplTest/SupplierServiceImplTest.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CustomerSupplierControllerCharacterizationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String adminToken() throws Exception {
    String email = uniqueCode("cs-admin") + "@test.local";
    TenantRegistrationRequest registration = new TenantRegistrationRequest(
        "Societe Customer Supplier Test", "x",
        AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        uniqueCode("CF"), null, email, "+237600000060", null,
        "Admin", "Test", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!");
    mockMvc.perform(post("/gestiondestock/v1/tenants/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registration)))
        .andExpect(status().isOk());
    MvcResult result = mockMvc.perform(post("/gestiondestock/v1/auth/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"login\":\"" + email + "\",\"password\":\"Test-Passw0rd!\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  @Test
  public void customerCrudFlowWorksOverHttp() throws Exception {
    String token = adminToken();

    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/customers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Client\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("cli") + "@test.local\","
                + "\"numTel\":\"600000001\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/customers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nom").value("Client"));

    mockMvc.perform(get("/gestiondestock/v1/customers/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/customers/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/customers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
  }

  @Test
  public void customerCreateWithMissingNomReturnsCleanValidationError() throws Exception {
    String token = adminToken();

    mockMvc.perform(post("/gestiondestock/v1/customers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mail\":\"" + uniqueCode("cli") + "@test.local\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_VALID"));
  }

  @Test
  public void supplierCrudFlowWorksOverHttp() throws Exception {
    String token = adminToken();

    MvcResult createResult = mockMvc.perform(post("/gestiondestock/v1/suppliers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nom\":\"Fournisseur\",\"prenom\":\"Test\",\"mail\":\"" + uniqueCode("fou") + "@test.local\","
                + "\"numTel\":\"600000002\",\"adresse\":{\"adresse1\":\"x\",\"ville\":\"x\",\"pays\":\"x\",\"codePostale\":\"00000\"}}"))
        .andExpect(status().isOk())
        .andReturn();
    long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(get("/gestiondestock/v1/suppliers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nom").value("Fournisseur"));

    mockMvc.perform(get("/gestiondestock/v1/suppliers/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(delete("/gestiondestock/v1/suppliers/delete/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    mockMvc.perform(get("/gestiondestock/v1/suppliers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
  }

  @Test
  public void supplierCreateWithMissingNomReturnsCleanValidationError() throws Exception {
    String token = adminToken();

    mockMvc.perform(post("/gestiondestock/v1/suppliers/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mail\":\"" + uniqueCode("fou") + "@test.local\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_VALID"));
  }
}
