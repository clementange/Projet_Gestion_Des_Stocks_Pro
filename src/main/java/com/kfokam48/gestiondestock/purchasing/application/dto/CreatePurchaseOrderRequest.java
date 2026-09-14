package com.kfokam48.gestiondestock.purchasing.application.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePurchaseOrderRequest {

  private PurchaseOrderDto order;

  private List<PurchaseOrderLineDto> lines;

}
