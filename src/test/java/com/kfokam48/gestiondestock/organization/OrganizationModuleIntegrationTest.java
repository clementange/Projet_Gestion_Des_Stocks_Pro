package com.kfokam48.gestiondestock.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.WarehouseService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import com.kfokam48.gestiondestock.organization.domain.model.SiteType;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest
public class OrganizationModuleIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private CityService cityService;

  @Autowired
  private SiteService siteService;

  @Autowired
  private WarehouseService warehouseService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  public void shouldCreateOrganizationCityAndWarehouseSuccessfully() {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe X").active(true).build());
    assertNotNull(organization.getId());

    CityDto city = cityService.save(
        CityDto.builder().name("Douala").country("Cameroun").organization(organization).build());
    assertNotNull(city.getId());

    SiteDto entrepot = siteService.save(
        SiteDto.builder().code(uniqueCode("DLA-ENT")).name("Entrepot Central Douala")
            .type(SiteType.ENTREPOT).active(true).city(city).build());
    assertNotNull(entrepot.getId());

    WarehouseDto warehouse = warehouseService.save(
        WarehouseDto.builder().code(uniqueCode("WH-DLA")).name("Entrepot Central Douala").site(entrepot).build());

    assertNotNull(warehouse.getId());
    assertEquals(entrepot.getId(), warehouse.getSite().getId());
    assertEquals(city.getId(), warehouse.getSite().getCity().getId());
    assertEquals(organization.getId(), warehouse.getSite().getCity().getOrganization().getId());
  }

  @Test
  public void shouldRejectWarehouseOnNonEntrepotSite() {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Y").active(true).build());
    CityDto city = cityService.save(
        CityDto.builder().name("Yaounde").organization(organization).build());
    SiteDto boutique = siteService.save(
        SiteDto.builder().code(uniqueCode("YAO-BTQ")).name("Boutique Bastos")
            .type(SiteType.BOUTIQUE).active(true).city(city).build());

    InvalidEntityException exception = assertThrows(InvalidEntityException.class, () -> warehouseService.save(
        WarehouseDto.builder().code(uniqueCode("WH-YAO")).name("Entrepot invalide").site(boutique).build()));

    assertEquals(1, exception.getErrors().size());
  }

  @Test
  public void shouldRejectOrganizationDeletionWhenCitiesExist() {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe Z").active(true).build());
    cityService.save(CityDto.builder().name("Bafoussam").organization(organization).build());

    assertThrows(InvalidOperationException.class, () -> organizationService.delete(organization.getId(), organization.getId()));
  }

  @Test
  public void shouldRejectSiteDeletionWhenWarehouseExists() {
    OrganizationDto organization = organizationService.save(
        OrganizationDto.builder().name("Societe W").active(true).build());
    CityDto city = cityService.save(CityDto.builder().name("Garoua").organization(organization).build());
    SiteDto entrepot = siteService.save(
        SiteDto.builder().code(uniqueCode("GAR-ENT")).name("Entrepot Garoua")
            .type(SiteType.ENTREPOT).active(true).city(city).build());
    warehouseService.save(WarehouseDto.builder().code(uniqueCode("WH-GAR")).name("Entrepot Garoua").site(entrepot).build());

    assertThrows(InvalidOperationException.class, () -> siteService.delete(entrepot.getId()));
  }

  // Phase 5b-2b : voir docs/phase-5b2b-report.md.
  @Test
  public void crossOrganizationSiteReadsAreScoped() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Societe Site A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Societe Site B").active(true).build());
    CityDto cityA = cityService.save(CityDto.builder().name("Douala").organization(orgA).build());
    SiteDto siteA = siteService.save(SiteDto.builder().code(uniqueCode("SITE")).name("Site A")
        .type(SiteType.ENTREPOT).active(true).city(cityA).build());

    assertEquals(siteA.getId(), siteService.findById(siteA.getId(), orgA.getId()).getId());
    assertEquals(siteA.getId(), siteService.findByCode(siteA.getCode(), orgA.getId()).getId());
    assertEquals(1, siteService.findAllByCity(cityA.getId(), orgA.getId()).size());
    assertEquals(1, siteService.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> siteService.findById(siteA.getId(), orgB.getId()));
    assertThrows(EntityNotFoundException.class, () -> siteService.findByCode(siteA.getCode(), orgB.getId()));
    assertEquals(0, siteService.findAllByCity(cityA.getId(), orgB.getId()).size());
    assertEquals(0, siteService.findAll(orgB.getId()).size());

    // findById(Long) sans organizationId reste utilise en interne (ReportingServiceImpl,
    // verifications d'appartenance de site a la creation de Sale/CustomerOrder/PurchaseOrder/
    // StockTransfer) : pas de filtrage, meme depuis un contexte "etranger".
    assertEquals(siteA.getId(), siteService.findById(siteA.getId()).getId());
  }

  // Phase 5b-2c : voir docs/phase-5b2c-report.md.
  @Test
  public void organizationAccessIsSelfOnly() {
    OrganizationDto orgA = organizationService.save(OrganizationDto.builder().name("Org Self A").active(true).build());
    OrganizationDto orgB = organizationService.save(OrganizationDto.builder().name("Org Self B").active(true).build());

    assertEquals(orgA.getId(), organizationService.findById(orgA.getId(), orgA.getId()).getId());
    assertEquals(1, organizationService.findAll(orgA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> organizationService.findById(orgA.getId(), orgB.getId()));
    // findAll(orgB) voit sa propre organisation (1 element), jamais orgA.
    assertEquals(1, organizationService.findAll(orgB.getId()).size());
    assertEquals(orgB.getId(), organizationService.findAll(orgB.getId()).get(0).getId());

    assertThrows(EntityNotFoundException.class, () -> organizationService.delete(orgA.getId(), orgB.getId()));
    // orgA existe toujours : la tentative de suppression depuis orgB a bien ete rejetee, pas seulement journalisee.
    assertEquals(orgA.getId(), organizationService.findById(orgA.getId(), orgA.getId()).getId());
  }
}
