package com.kfokam48.gestiondestock.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.tenant.application.TenantRegistrationService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 16 : TenantRegistrationService.register() doit creer une Organization miroir + un Site
 * par defaut. Phase 4b : reecrit (pas simplement adapte) - l'ancien point d'entree
 * EntrepriseServiceImpl.save() a disparu au profit de TenantRegistrationService.register(), qui
 * exige desormais des identifiants admin explicites (voir docs/phase-4b-report.md). Memes
 * assertions finales qu'avant la reecriture.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class EntrepriseServiceOrganizationMirrorIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TenantRegistrationService tenantRegistrationService;

  @Autowired
  private OrganizationService organizationService;

  private static String uniqueEmail() {
    return "mirror-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
  }

  @Test
  public void creatingAnEntrepriseCreatesAMirrorOrganizationWithDefaultSite() {
    String email = uniqueEmail();
    TenantDto tenant = tenantRegistrationService.register(new TenantRegistrationRequest(
        "Societe Miroir", "Test miroir Organization",
        AdresseDto.builder().adresse1("1 Rue Miroir").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        "CF-" + UUID.randomUUID().toString().substring(0, 8), null, email, "+237600000003", null,
        "Admin", "Miroir", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!"));

    assertNotNull(tenant.getOrganizationId());

    OrganizationDto organization = organizationService.findById(tenant.getOrganizationId());
    assertEquals("Societe Miroir", organization.getName());
    assertEquals("Test miroir Organization", organization.getDescription());

    SiteDto defaultSite = organizationService.ensureDefaultSite(organization.getId());
    assertNotNull(defaultSite.getId());
    assertEquals("ORG-" + organization.getId() + "-DEFAULT", defaultSite.getCode());
  }

}
