package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.organization.application.WarehouseService;
import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
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
  public WarehouseDto save(@RequestBody WarehouseDto dto) {
    return warehouseService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/warehouses/{idWarehouse}", produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto findById(@PathVariable("idWarehouse") Long id) {
    return warehouseService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/warehouses/filter/{codeWarehouse}", produces = MediaType.APPLICATION_JSON_VALUE)
  public WarehouseDto findByCode(@PathVariable("codeWarehouse") String code) {
    return warehouseService.findByCode(code);
  }

  @GetMapping(value = APP_ROOT + "/warehouses/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<WarehouseDto> findAll() {
    return warehouseService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/warehouses/delete/{idWarehouse}")
  public void delete(@PathVariable("idWarehouse") Long id) {
    warehouseService.delete(id);
  }
}
