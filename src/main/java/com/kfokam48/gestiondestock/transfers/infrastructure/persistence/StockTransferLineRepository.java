package com.kfokam48.gestiondestock.transfers.infrastructure.persistence;

import com.kfokam48.gestiondestock.transfers.domain.model.StockTransferLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferLineRepository extends JpaRepository<StockTransferLine, Long> {

  List<StockTransferLine> findAllByStockTransferId(Long stockTransferId);

}
