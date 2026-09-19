package com.kfokam48.gestiondestock.sales.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
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

/**
 * Phase 4d : contrat canonique pour les clients (successeur de {@code /clients/*}, supprime -
 * voir docs/phase-4d-report.md). N'existait pas avant cet increment : {@code sales.Customer}
 * n'etait expose que via l'adaptateur legacy {@code ClientController}.
 */
@Tag(name = "customers")
@RestController
public class CustomerController {

  private CustomerService customerService;

  @Autowired
  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping(value = APP_ROOT + "/customers/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerDto create(@RequestBody CustomerDto dto) {
    return customerService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/customers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerDto findById(@PathVariable("id") Long id) {
    return customerService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/customers/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CustomerDto> findAll() {
    return customerService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/customers/delete/{id}")
  public void delete(@PathVariable("id") Long id) {
    customerService.delete(id);
  }
}
