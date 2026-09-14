package com.kfokam48.gestiondestock.sales.infrastructure.persistence;

import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderLineRepository extends JpaRepository<CustomerOrderLine, Long> {

  List<CustomerOrderLine> findAllByCustomerOrderId(Long customerOrderId);

  List<CustomerOrderLine> findAllByArticleId(Long articleId);

}
