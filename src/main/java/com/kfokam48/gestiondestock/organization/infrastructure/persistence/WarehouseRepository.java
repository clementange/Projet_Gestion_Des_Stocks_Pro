package com.kfokam48.gestiondestock.organization.infrastructure.persistence;

import com.kfokam48.gestiondestock.organization.domain.model.Warehouse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

  Optional<Warehouse> findWarehouseByCode(String code);

  Optional<Warehouse> findWarehouseBySiteId(Long siteId);

}
