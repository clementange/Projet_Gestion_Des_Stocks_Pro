package com.kfokam48.gestiondestock.transfers.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.organization.application.SiteService;
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
  private SiteService siteService;

  @Autowired
  public StockTransferServiceImpl(StockTransferRepository stockTransferRepository,
      StockTransferLineRepository stockTransferLineRepository, InventoryFacade inventoryFacade,
      AuthorizationService authorizationService, SiteService siteService) {
    this.stockTransferRepository = stockTransferRepository;
    this.stockTransferLineRepository = stockTransferLineRepository;
    this.inventoryFacade = inventoryFacade;
    this.authorizationService = authorizationService;
    this.siteService = siteService;
  }

  @Override
  public StockTransferDto create(StockTransferDto dto, List<StockTransferLineDto> lines, Long organizationId) {
    List<String> errors = StockTransferValidator.validate(dto, lines);
    if (!errors.isEmpty()) {
      log.error("StockTransfer is not valid {}", dto);
      throw new InvalidEntityException("Le transfert n'est pas valide", ErrorCodes.STOCK_TRANSFER_NOT_VALID, errors);
    }
    // Phase 5b-2b : StockTransfer.create n'avait aucune verification de permission ni
    // d'appartenance des sites (origine ET destination) a l'organisation de l'appelant - voir
    // docs/phase-5b2b-report.md.
    requireSiteInOrganization(dto.getOriginSite().getId(), organizationId);
    requireSiteInOrganization(dto.getDestinationSite().getId(), organizationId);

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
  public StockTransferDto findById(Long id, Long organizationId) {
    StockTransfer transfer = fetchTransfer(id);
    if (!belongsToOrganization(transfer, organizationId)) {
      throw new EntityNotFoundException(
          "Aucun transfert avec l'ID = " + id + " n'a ete trouve dans la BDD",
          ErrorCodes.STOCK_TRANSFER_NOT_FOUND);
    }
    return StockTransferDto.fromEntity(transfer);
  }

  @Override
  @Transactional(readOnly = true)
  public StockTransferDto findByCode(String code, Long organizationId) {
    if (!StringUtils.hasLength(code)) {
      log.error("StockTransfer CODE is null");
      return null;
    }
    return stockTransferRepository.findStockTransferByCode(code)
        .filter(transfer -> belongsToOrganization(transfer, organizationId))
        .map(StockTransferDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun transfert avec le CODE = " + code + " n'a ete trouve dans la BDD",
            ErrorCodes.STOCK_TRANSFER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<StockTransferDto> findAll(Long organizationId) {
    return stockTransferRepository.findAll().stream()
        .filter(transfer -> belongsToOrganization(transfer, organizationId))
        .map(StockTransferDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2b : exige que les DEUX sites (origine et destination) appartiennent a
  // l'organisation de l'appelant - voir docs/phase-5b2b-report.md.
  private boolean belongsToOrganization(StockTransfer transfer, Long organizationId) {
    return organizationId != null
        && siteBelongsToOrganization(transfer.getOriginSite(), organizationId)
        && siteBelongsToOrganization(transfer.getDestinationSite(), organizationId);
  }

  private boolean siteBelongsToOrganization(com.kfokam48.gestiondestock.organization.domain.model.Site site, Long organizationId) {
    return site != null
        && site.getCity() != null
        && site.getCity().getOrganization() != null
        && organizationId.equals(site.getCity().getOrganization().getId());
  }

  private void requireSiteInOrganization(Long siteId, Long organizationId) {
    com.kfokam48.gestiondestock.organization.application.dto.SiteDto site = siteService.findById(siteId);
    boolean matches = site != null && site.getCity() != null && site.getCity().getOrganization() != null
        && organizationId != null && organizationId.equals(site.getCity().getOrganization().getId());
    if (!matches) {
      log.warn("Site {} does not belong to organization {}", siteId, organizationId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'enregistrer un transfert sur ce site",
          ErrorCodes.STOCK_TRANSFER_ACCESS_DENIED);
    }
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
  public StockTransferDto ship(Long id, Long userId, Long organizationId) {
    StockTransfer transfer = fetchTransfer(id);
    if (!authorizationService.hasPermission(userId, STOCK_TRANSFER_SHIP, ScopeType.SITE, transfer.getOriginSite().getId(), organizationId)) {
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
  public StockTransferDto receive(Long id, Long userId, Long organizationId) {
    StockTransfer transfer = fetchTransfer(id);
    if (!authorizationService.hasPermission(userId, STOCK_TRANSFER_RECEIVE, ScopeType.SITE, transfer.getDestinationSite().getId(), organizationId)) {
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
