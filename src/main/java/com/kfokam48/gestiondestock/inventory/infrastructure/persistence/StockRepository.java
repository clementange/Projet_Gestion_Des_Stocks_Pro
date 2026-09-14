package com.kfokam48.gestiondestock.inventory.infrastructure.persistence;

import com.kfokam48.gestiondestock.inventory.domain.model.Stock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

  Optional<Stock> findByArticleIdAndSiteId(Long articleId, Long siteId);

  List<Stock> findAllByArticleId(Long articleId);

  List<Stock> findAllBySiteId(Long siteId);

}
