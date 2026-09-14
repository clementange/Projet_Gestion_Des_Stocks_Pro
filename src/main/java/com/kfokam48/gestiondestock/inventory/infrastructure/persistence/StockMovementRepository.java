package com.kfokam48.gestiondestock.inventory.infrastructure.persistence;

import com.kfokam48.gestiondestock.inventory.domain.model.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  List<StockMovement> findAllByArticleIdAndSiteId(Long articleId, Long siteId);

  List<StockMovement> findAllByArticleId(Long articleId);

}
