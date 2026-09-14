package com.kfokam48.gestiondestock.transfers.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.transfers.application.StockTransferService;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
import com.kfokam48.gestiondestock.transfers.application.validator.StockTransferValidator;
import com.kfokam48.gestiondestock.transfers.domain.model.StockTransfer;
import com.kfokam48.gestiondestock.transfers.domain.model.StockTransferLine;
import com.kfokam48.gestiondestock.transfers.infrastructure.persistence.StockTransferLineRepository;
import com.kfokam48.gestiondestock.transfers.infrastructure.persistence.StockTransferRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@Transactional
public class StockTransferServiceImpl implements StockTransferService {

  private static final String STOCK_TRANSFER_SHIP = "STOCK_TRANSFER_SHIP";
  private static final String STOCK_TRANSFER_RECEIVE = "STOCK_TRANSFER_RECEIVE";

  private StockTransferRepository stockTransferRepository;
  private StockTransferLineRepository stockTransferLineRepository;
  private InventoryFacade inventoryFacade;
  private AuthorizationService authorizationService;

  @Autowired
  public StockTransferServiceImpl(StockTransferRepository stockTransferRepository,
      StockTransferLineRepository stockTransferLineRepository, InventoryFacade inventoryFacade,
      AuthorizationService authorizationService) {
    this.stockTransferRepository = stockTransferRepository;
    this.stockTransferLineRepository = stockTransferLineRepository;
    this.inventoryFacade = inventoryFacade;
    this.authorizationService = authorizationService;
  }

  @Override
  public StockTransferDto create(StockTransferDto dto, List<StockTransferLineDto> lines) {
    List<String> errors = StockTransferValidator.validate(dto, lines);
    if (!errors.isEmpty()) {
      log.error("StockTransfer is not valid {}", dto);
      throw new InvalidEntityException("Le transfert n'est pas valide", ErrorCodes.STOCK_TRANSFER_NOT_VALID, errors);
    }

    StockTransfer transfer = StockTransferDto.toEntity(dto);
    transfer.setRequestDate(Instant.now());
    StockTransfer saved = stockTransferRepository.save(transfer);

    lines.forEach(lineDto -> {
      StockTransferLine line = StockTransferLineDto.toNewEntity(lineDto);
      line.setStockTransfer(saved);
      stockTransferLineRepository.save(line);
    });

    return StockTransferDto.fromEntity(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public StockTransferDto findById(Long id) {
    return StockTransferDto.fromEntity(fetchTransfer(id));
  }

  @Override
  @Transactional(readOnly = true)
  public StockTransferDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("StockTransfer CODE is null");
      return null;
    }
    return stockTransferRepository.findStockTransferByCode(code)
        .map(StockTransferDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun transfert avec le CODE = " + code + " n'a ete trouve dans la BDD",
            ErrorCodes.STOCK_TRANSFER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockTransferDto> findAll() {
    return stockTransferRepository.findAll().stream()
        .map(StockTransferDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockTransferLineDto> findLines(Long stockTransferId) {
    return stockTransferLineRepository.findAllByStockTransferId(stockTransferId).stream()
        .map(StockTransferLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public StockTransferDto submit(Long id) {
    StockTransfer transfer = fetchTransfer(id);
    transfer.submit();
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  @Override
  public StockTransferDto approve(Long id, Long approverUserId) {
    StockTransfer transfer = fetchTransfer(id);
    transfer.approve(approverUserId);
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  @Override
  public StockTransferDto startPreparation(Long id) {
    StockTransfer transfer = fetchTransfer(id);
    transfer.requirePreparable();
    transfer.markInPreparation();
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  @Override
  public StockTransferDto ship(Long id, Long userId) {
    StockTransfer transfer = fetchTransfer(id);
    if (!authorizationService.hasPermission(userId, STOCK_TRANSFER_SHIP, ScopeType.SITE, transfer.getOriginSite().getId())) {
      log.warn("User {} tried to ship transfer {} from site {} without permission",
          userId, id, transfer.getOriginSite().getId());
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'expedier ce transfert depuis ce site",
          ErrorCodes.STOCK_TRANSFER_ACCESS_DENIED);
    }
    transfer.requireShippable();

    List<StockTransferLine> lines = stockTransferLineRepository.findAllByStockTransferId(id);
    for (StockTransferLine line : lines) {
      inventoryFacade.transferOut(line.getArticle().getId(), transfer.getOriginSite().getId(), line.getQuantite(),
          transfer.getCode(), userId);
    }

    transfer.markShipped();
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  @Override
  public StockTransferDto receive(Long id, Long userId) {
    StockTransfer transfer = fetchTransfer(id);
    if (!authorizationService.hasPermission(userId, STOCK_TRANSFER_RECEIVE, ScopeType.SITE, transfer.getDestinationSite().getId())) {
      log.warn("User {} tried to receive transfer {} at site {} without permission",
          userId, id, transfer.getDestinationSite().getId());
      throw new InvalidOperationException(
          "Vous n'avez pas la permission de receptionner ce transfert sur ce site",
          ErrorCodes.STOCK_TRANSFER_ACCESS_DENIED);
    }
    transfer.requireReceivable();

    List<StockTransferLine> lines = stockTransferLineRepository.findAllByStockTransferId(id);
    for (StockTransferLine line : lines) {
      inventoryFacade.transferIn(line.getArticle().getId(), transfer.getDestinationSite().getId(), line.getQuantite(),
          transfer.getCode(), userId);
    }

    transfer.markReceived();
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  @Override
  public StockTransferDto cancel(Long id) {
    StockTransfer transfer = fetchTransfer(id);
    transfer.cancel();
    return StockTransferDto.fromEntity(stockTransferRepository.save(transfer));
  }

  private StockTransfer fetchTransfer(Long id) {
    if (id == null) {
      log.error("StockTransfer ID is null");
      throw new EntityNotFoundException("L'ID du transfert est null", ErrorCodes.STOCK_TRANSFER_NOT_FOUND);
    }
    return stockTransferRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun transfert avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.STOCK_TRANSFER_NOT_FOUND)
        );
  }
}
