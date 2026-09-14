package com.kfokam48.gestiondestock.purchasing.infrastructure.persistence;

import com.kfokam48.gestiondestock.purchasing.domain.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

}
