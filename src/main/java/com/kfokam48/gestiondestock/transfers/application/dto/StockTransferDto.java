package com.kfokam48.gestiondestock.transfers.application.dto;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import com.kfokam48.gestiondestock.transfers.domain.model.StockTransfer;
import com.kfokam48.gestiondestock.transfers.domain.model.TransferStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockTransferDto {

  private Long id;

  private String code;

  private SiteDto originSite;

  private SiteDto destinationSite;

  private TransferStatus status;

  private Long requestedByUserId;

  private Long approvedByUserId;

  private Instant requestDate;

  public static StockTransferDto fromEntity(StockTransfer transfer) {
    if (transfer == null) {
      return null;
    }

    return StockTransferDto.builder()
        .id(transfer.getId())
        .code(transfer.getCode())
        .originSite(SiteDto.fromEntity(transfer.getOriginSite()))
        .destinationSite(SiteDto.fromEntity(transfer.getDestinationSite()))
        .status(transfer.getStatus())
        .requestedByUserId(transfer.getRequestedByUserId())
        .approvedByUserId(transfer.getApprovedByUserId())
        .requestDate(transfer.getRequestDate())
        .build();
  }

  public static StockTransfer toEntity(StockTransferDto dto) {
    if (dto == null) {
      return null;
    }

    StockTransfer transfer = new StockTransfer();
    transfer.setId(dto.getId());
    transfer.setCode(dto.getCode());
    transfer.setOriginSite(SiteDto.toEntity(dto.getOriginSite()));
    transfer.setDestinationSite(SiteDto.toEntity(dto.getDestinationSite()));
    transfer.setStatus(dto.getStatus() != null ? dto.getStatus() : TransferStatus.BROUILLON);
    transfer.setRequestedByUserId(dto.getRequestedByUserId());
    transfer.setApprovedByUserId(dto.getApprovedByUserId());
    transfer.setRequestDate(dto.getRequestDate());

    return transfer;
  }
}
