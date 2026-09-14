package com.kfokam48.gestiondestock.purchasing.application;

import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import java.util.List;

public interface SupplierService {

  SupplierDto save(SupplierDto dto);

  SupplierDto findById(Long id);

  List<SupplierDto> findAll();

  void delete(Long id);

}
