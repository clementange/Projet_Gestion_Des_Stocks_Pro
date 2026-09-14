package com.kfokam48.gestiondestock.sales.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import com.kfokam48.gestiondestock.sales.application.validator.CustomerValidator;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrder;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderRepository;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
@Slf4j
public class CustomerServiceImpl implements CustomerService {

  private CustomerRepository customerRepository;
  private CustomerOrderRepository customerOrderRepository;

  @Autowired
  public CustomerServiceImpl(CustomerRepository customerRepository, CustomerOrderRepository customerOrderRepository) {
    this.customerRepository = customerRepository;
    this.customerOrderRepository = customerOrderRepository;
  }

  @Override
  public CustomerDto save(CustomerDto dto) {
    List<String> errors = CustomerValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Customer is not valid {}", dto);
      throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CUSTOMER_NOT_VALID, errors);
    }
    return CustomerDto.fromEntity(
        customerRepository.save(CustomerDto.toEntity(dto))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerDto findById(Long id) {
    if (id == null) {
      log.error("Customer ID is null");
      return null;
    }
    return customerRepository.findById(id)
        .map(CustomerDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.CUSTOMER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerDto> findAll() {
    return customerRepository.findAll().stream()
        .map(CustomerDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Customer ID is null");
      return;
    }
    List<CustomerOrder> orders = customerOrderRepository.findAllByCustomerId(id);
    if (!orders.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer ce client qui est deja utilise",
          ErrorCodes.CUSTOMER_ALREADY_IN_USE);
    }
    customerRepository.deleteById(id);
  }
}
