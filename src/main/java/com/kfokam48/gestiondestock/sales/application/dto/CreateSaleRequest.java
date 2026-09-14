package com.kfokam48.gestiondestock.sales.application.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateSaleRequest {

  private SaleDto sale;

  private List<SaleLineDto> lines;

}
