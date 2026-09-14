package com.kfokam48.gestiondestock.transfers.application;

import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import java.util.List;

public interface StockTransferService {

  StockTransferDto create(StockTransferDto dto, List<StockTransferLineDto> lines);

  StockTransferDto findById(Long id);

  StockTransferDto findByCode(String code);

  List<StockTransferDto> findAll();

  List<StockTransferLineDto> findLines(Long stockTransferId);

  StockTransferDto submit(Long id);

  StockTransferDto approve(Long id, Long approverUserId);

  StockTransferDto startPreparation(Long id);

  /**
   * EN_PREPARATION -> EXPEDIE : sort physiquement le stock du site d'origine, ligne par ligne
   * (StockMovementType.TRANSFERT_SORTIE).
   */
  StockTransferDto ship(Long id, Long userId);

  /**
   * EXPEDIE -> RECU : entre physiquement le stock au site de destination, ligne par ligne
   * (StockMovementType.TRANSFERT_ENTREE). C'est le moment ou A-30/B+30 devient reel (Test 3,
   * section 43).
   */
  StockTransferDto receive(Long id, Long userId);

  StockTransferDto cancel(Long id);

}
