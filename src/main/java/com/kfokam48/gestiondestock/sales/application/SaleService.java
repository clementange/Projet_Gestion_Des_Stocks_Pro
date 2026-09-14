package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import java.time.Instant;
import java.util.List;

public interface SaleService {

  /**
   * Cree la vente et sort le stock immediatement, ligne par ligne, via InventoryFacade. Si une
   * seule ligne echoue par manque de stock, toute la vente est annulee (rollback transactionnel)
   * : pas de vente partiellement enregistree.
   */
  SaleDto create(SaleDto dto, List<SaleLineDto> lines, Long userId);

  SaleDto findById(Long id);

  SaleDto findByCode(String code);

  List<SaleDto> findAll();

  List<SaleLineDto> findLines(Long saleId);

  List<SaleDto> findAllBySiteAndPeriod(Long siteId, Instant from, Instant to);

}
