package com.kfokam48.gestiondestock.purchasing.infrastructure.persistence;

import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

  Optional<PurchaseOrder> findPurchaseOrderByCode(String code);

  List<PurchaseOrder> findAllBySupplierId(Long supplierId);

}
