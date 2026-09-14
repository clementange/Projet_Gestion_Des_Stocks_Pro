package com.kfokam48.gestiondestock.purchasing.application.dto;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrder;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PurchaseOrderDto {

  private Long id;

  private String code;

  private Long supplierId;

  private SiteDto site;

  private Instant orderDate;

  private PurchaseOrderStatus status;

  public static PurchaseOrderDto fromEntity(PurchaseOrder purchaseOrder) {
    if (purchaseOrder == null) {
      return null;
    }

    return PurchaseOrderDto.builder()
        .id(purchaseOrder.getId())
        .code(purchaseOrder.getCode())
        .supplierId(purchaseOrder.getSupplierId())
        .site(SiteDto.fromEntity(purchaseOrder.getSite()))
        .orderDate(purchaseOrder.getOrderDate())
        .status(purchaseOrder.getStatus())
        .build();
  }

  public static PurchaseOrder toEntity(PurchaseOrderDto dto) {
    if (dto == null) {
      return null;
    }

    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setId(dto.getId());
    purchaseOrder.setCode(dto.getCode());
    purchaseOrder.setSupplierId(dto.getSupplierId());
    purchaseOrder.setSite(SiteDto.toEntity(dto.getSite()));
    purchaseOrder.setOrderDate(dto.getOrderDate());
    purchaseOrder.setStatus(dto.getStatus() != null ? dto.getStatus() : PurchaseOrderStatus.BROUILLON);

    return purchaseOrder;
  }
}
