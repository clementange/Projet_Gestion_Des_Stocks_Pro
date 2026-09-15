package com.kfokam48.gestiondestock.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.dto.EntrepriseDto;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 16 : EntrepriseServiceImpl.save() doit creer une Organization miroir + un Site par
 * defaut, sans changer le contrat JSON de /entreprises/create (EntrepriseDto reste inchange).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class EntrepriseServiceOrganizationMirrorIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private EntrepriseService entrepriseService;

  @Autowired
  private EntrepriseRepository entrepriseRepository;

  @Autowired
  private OrganizationService organizationService;

  private static String uniqueEmail() {
    return "mirror-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
  }

  @Test
  public void creatingAnEntrepriseCreatesAMirrorOrganizationWithDefaultSite() {
    EntrepriseDto entreprise = entrepriseService.save(EntrepriseDto.builder()
        .nom("Societe Miroir")
        .description("Test miroir Organization")
        .codeFiscal("CF-" + UUID.randomUUID().toString().substring(0, 8))
        .email(uniqueEmail())
        .numTel("+237600000003")
        .adresse(AdresseDto.builder().adresse1("1 Rue Miroir").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build());

    Entreprise persisted = entrepriseRepository.findById(entreprise.getId()).orElseThrow();
    assertNotNull(persisted.getOrganizationId());

    OrganizationDto organization = organizationService.findById(persisted.getOrganizationId());
    assertEquals("Societe Miroir", organization.getName());
    assertEquals("Test miroir Organization", organization.getDescription());

    SiteDto defaultSite = organizationService.ensureDefaultSite(organization.getId());
    assertNotNull(defaultSite.getId());
    assertEquals("ORG-" + organization.getId() + "-DEFAULT", defaultSite.getCode());
  }

}
