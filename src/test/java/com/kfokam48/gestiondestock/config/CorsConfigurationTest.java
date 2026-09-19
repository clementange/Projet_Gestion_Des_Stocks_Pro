package com.kfokam48.gestiondestock.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 5a : {@code allowedOriginPatterns("*")} combine a {@code allowCredentials(true)} etait la
 * combinaison explicitement interdite par CLAUDE.md (zero-tolerance) - voir
 * SecurityConfiguration.corsFilter et docs/phase-5a-report.md. CorsFilter s'execute avant tout
 * filtre d'authentification : verifiable sur n'importe quelle route, meme protegee.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CorsConfigurationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  public void allowedOriginReceivesAccessControlAllowOriginHeader() throws Exception {
    mockMvc.perform(get("/gestiondestock/v1/tenants/all").header("Origin", "http://localhost:4200"))
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
  }

  @Test
  public void arbitraryOriginDoesNotReceiveAccessControlAllowOriginHeader() throws Exception {
    mockMvc.perform(get("/gestiondestock/v1/tenants/all").header("Origin", "http://evil.example.com"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
