package com.kfokam48.gestiondestock.transfers.application;

import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import java.util.List;

public interface StockTransferService {

  // Phase 5b-2b : organizationId verifie contre celui des DEUX sites du transfert (origine et
  // destination, derives via Site -> City -> Organization) avant creation - voir
  // docs/phase-5b2b-report.md.
  StockTransferDto create(StockTransferDto dto, List<StockTransferLineDto> lines, Long organizationId);

  StockTransferDto findById(Long id, Long organizationId);

  StockTransferDto findByCode(String code, Long organizationId);

  List<StockTransferDto> findAll(Long organizationId);

  List<StockTransferLineDto> findLines(Long stockTransferId);

  StockTransferDto submit(Long id);

  StockTransferDto approve(Long id, Long approverUserId);

  StockTransferDto startPreparation(Long id);

  /**
   * EN_PREPARATION -> EXPEDIE : sort physiquement le stock du site d'origine, ligne par ligne
   * (StockMovementType.TRANSFERT_SORTIE).
   */
  StockTransferDto ship(Long id, Long userId, Long organizationId);

  /**
   * EXPEDIE -> RECU : entre physiquement le stock au site de destination, ligne par ligne
   * (StockMovementType.TRANSFERT_ENTREE). C'est le moment ou A-30/B+30 devient reel (Test 3,
   * section 43).
   */
  StockTransferDto receive(Long id, Long userId, Long organizationId);

  StockTransferDto cancel(Long id);

}
