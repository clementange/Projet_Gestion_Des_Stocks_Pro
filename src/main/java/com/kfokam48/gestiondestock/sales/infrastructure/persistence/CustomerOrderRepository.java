package com.kfokam48.gestiondestock.sales.infrastructure.persistence;

import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

  Optional<CustomerOrder> findCustomerOrderByCode(String code);

  List<CustomerOrder> findAllByCustomerId(Long customerId);

}
