package com.kfokam48.gestiondestock.sales.application.dto;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrder;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerOrderDto {

  private Long id;

  private String code;

  private Long customerId;

  private SiteDto site;

  private Instant orderDate;

  private CustomerOrderStatus status;

  public static CustomerOrderDto fromEntity(CustomerOrder order) {
    if (order == null) {
      return null;
    }

    return CustomerOrderDto.builder()
        .id(order.getId())
        .code(order.getCode())
        .customerId(order.getCustomerId())
        .site(SiteDto.fromEntity(order.getSite()))
        .orderDate(order.getOrderDate())
        .status(order.getStatus())
        .build();
  }

  public static CustomerOrder toEntity(CustomerOrderDto dto) {
    if (dto == null) {
      return null;
    }

    CustomerOrder order = new CustomerOrder();
    order.setId(dto.getId());
    order.setCode(dto.getCode());
    order.setCustomerId(dto.getCustomerId());
    order.setSite(SiteDto.toEntity(dto.getSite()));
    order.setOrderDate(dto.getOrderDate());
    order.setStatus(dto.getStatus() != null ? dto.getStatus() : CustomerOrderStatus.BROUILLON);

    return order;
  }
}
