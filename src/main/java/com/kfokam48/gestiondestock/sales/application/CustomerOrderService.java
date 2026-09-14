package com.kfokam48.gestiondestock.sales.application;

import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import java.util.List;

public interface CustomerOrderService {

  CustomerOrderDto create(CustomerOrderDto dto, List<CustomerOrderLineDto> lines);

  CustomerOrderDto findById(Long id);

  CustomerOrderDto findByCode(String code);

  List<CustomerOrderDto> findAll();

  List<CustomerOrderLineDto> findLines(Long customerOrderId);

  CustomerOrderDto validate(Long id);

  /**
   * VALIDEE -> RESERVEE : reserve le stock disponible de chaque ligne (StockMovement
   * RESERVATION). Refuse si le stock disponible d'une seule ligne est insuffisant.
   */
  CustomerOrderDto reserve(Long id, Long userId);

  CustomerOrderDto prepare(Long id);

  CustomerOrderDto ship(Long id);

  /**
   * EXPEDIEE -> LIVREE : sortie physique + liberation de la reservation de chaque ligne.
   */
  CustomerOrderDto deliver(Long id, Long userId);

  CustomerOrderDto cancel(Long id, Long userId);

}
