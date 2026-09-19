package com.kfokam48.gestiondestock.sales.infrastructure.persistence;

import com.kfokam48.gestiondestock.sales.domain.model.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

  Optional<Customer> findByMail(String mail);

}
