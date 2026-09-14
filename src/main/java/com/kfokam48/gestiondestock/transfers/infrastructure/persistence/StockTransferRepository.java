package com.kfokam48.gestiondestock.transfers.infrastructure.persistence;

import com.kfokam48.gestiondestock.transfers.domain.model.StockTransfer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

  Optional<StockTransfer> findStockTransferByCode(String code);

}
