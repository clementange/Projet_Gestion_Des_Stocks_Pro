package com.kfokam48.gestiondestock.sales.infrastructure.persistence;

import com.kfokam48.gestiondestock.sales.domain.model.SaleLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

  List<SaleLine> findAllBySaleId(Long saleId);

  List<SaleLine> findAllByArticleId(Long articleId);

}
