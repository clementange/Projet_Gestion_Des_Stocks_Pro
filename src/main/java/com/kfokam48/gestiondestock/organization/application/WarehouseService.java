package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.WarehouseDto;
import java.util.List;

public interface WarehouseService {

  WarehouseDto save(WarehouseDto dto);

  WarehouseDto findById(Long id);

  WarehouseDto findByCode(String code);

  List<WarehouseDto> findAll();

  void delete(Long id);

}
