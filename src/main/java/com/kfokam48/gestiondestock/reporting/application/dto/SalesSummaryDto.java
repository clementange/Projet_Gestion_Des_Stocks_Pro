package com.kfokam48.gestiondestock.reporting.application.dto;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SalesSummaryDto {

  private SiteDto site;

  private Instant from;

  private Instant to;

  private int saleCount;

  private BigDecimal totalQuantity;

  private BigDecimal totalAmount;

}
