package com.kfokam48.gestiondestock.sales.infrastructure.persistence;

import com.kfokam48.gestiondestock.sales.domain.model.Sale;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {

  Optional<Sale> findSaleByCode(String code);

  List<Sale> findAllBySiteIdAndSaleDateBetween(Long siteId, Instant from, Instant to);

}
