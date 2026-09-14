package com.kfokam48.gestiondestock.purchasing.application;

import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import java.math.BigDecimal;
import java.util.List;

public interface PurchaseOrderService {

  PurchaseOrderDto create(PurchaseOrderDto dto, List<PurchaseOrderLineDto> lines);

  PurchaseOrderDto findById(Long id);

  PurchaseOrderDto findByCode(String code);

  List<PurchaseOrderDto> findAll();

  List<PurchaseOrderLineDto> findLines(Long purchaseOrderId);

  PurchaseOrderDto validate(Long id);

  PurchaseOrderDto cancel(Long id);

  /**
   * Reception explicite d'une ligne : c'est le SEUL point d'entree qui fait entrer du stock
   * (via InventoryFacade), jamais la creation ni la validation de la commande (section 14).
   */
  PurchaseOrderLineDto receiveLine(Long purchaseOrderId, Long purchaseOrderLineId, BigDecimal quantity, Long userId);

}
