package com.kfokam48.gestiondestock.purchasing.infrastructure.persistence;

import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

  List<PurchaseOrderLine> findAllByPurchaseOrderId(Long purchaseOrderId);

  List<PurchaseOrderLine> findAllByArticleId(Long articleId);

}
