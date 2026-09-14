package com.kfokam48.gestiondestock.transfers.application.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateStockTransferRequest {

  private StockTransferDto transfer;

  private List<StockTransferLineDto> lines;

}
