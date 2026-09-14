package com.kfokam48.gestiondestock.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

@RunWith(SpringRunner.class)
@SpringBootTest
public class OrganizationModuleIntegrationTest {

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

    assertThrows(InvalidOperationException.class, () -> organizationService.delete(organization.getId()));
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
}
