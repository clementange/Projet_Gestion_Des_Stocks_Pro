package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.organization.application.WarehouseService;
import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "warehouses")
@RestController
public class WarehouseController {

  private WarehouseService warehouseService;

  @Autowired
  public WarehouseController(WarehouseService warehouseService) {
    this.warehouseService = warehouseService;
  }

  @PostMapping(value = APP_ROOT + "/warehouses/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto save(@RequestBody WarehouseDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    return warehouseService.save(dto, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/warehouses/{idWarehouse}", produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto findById(@PathVariable("idWarehouse") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return warehouseService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/warehouses/filter/{codeWarehouse}", produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto findByCode(@PathVariable("codeWarehouse") String code, @AuthenticationPrincipal ExtendedUser principal) {
    return warehouseService.findByCode(code, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/warehouses/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<WarehouseDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return warehouseService.findAll(principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/warehouses/delete/{idWarehouse}")
  public void delete(@PathVariable("idWarehouse") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    warehouseService.delete(id, principal.getOrganizationId());
  }

  /**
   * Phase 5b-2g : seul chemin de mise a jour. L'id vient du CHEMIN ; le service verifie
   * l'appartenance a l'organisation de l'appelant avant toute ecriture et interdit tout
   * changement d'organisation - voir docs/phase-5b2g-report.md.
   */
  @PutMapping(value = APP_ROOT + "/warehouses/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto update(@PathVariable("id") Long id, @RequestBody WarehouseDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    return warehouseService.update(id, dto, principal.getOrganizationId());
  }
}
