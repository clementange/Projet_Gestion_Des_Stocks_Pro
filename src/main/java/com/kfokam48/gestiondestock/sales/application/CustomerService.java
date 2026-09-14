package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import java.util.List;

public interface CustomerService {

  CustomerDto save(CustomerDto dto);

  CustomerDto findById(Long id);

  List<CustomerDto> findAll();

  void delete(Long id);

}
