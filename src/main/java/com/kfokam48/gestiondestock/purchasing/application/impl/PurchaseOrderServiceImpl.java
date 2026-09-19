package com.kfokam48.gestiondestock.purchasing.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import com.kfokam48.gestiondestock.purchasing.application.validator.PurchaseOrderValidator;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrder;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderLine;
import com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.PurchaseOrderLineRepository;
import com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import java.math.BigDecimal;
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
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

  private static final String PURCHASE_ORDER_RECEIVE = "PURCHASE_ORDER_RECEIVE";

  private PurchaseOrderRepository purchaseOrderRepository;
  private PurchaseOrderLineRepository purchaseOrderLineRepository;
  private InventoryFacade inventoryFacade;
  private AuthorizationService authorizationService;

  @Autowired
  public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
      PurchaseOrderLineRepository purchaseOrderLineRepository, InventoryFacade inventoryFacade,
      AuthorizationService authorizationService) {
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.purchaseOrderLineRepository = purchaseOrderLineRepository;
    this.inventoryFacade = inventoryFacade;
    this.authorizationService = authorizationService;
  }

  @Override
  public PurchaseOrderDto create(PurchaseOrderDto dto, List<PurchaseOrderLineDto> lines) {
    List<String> errors = PurchaseOrderValidator.validate(dto, lines);
    if (!errors.isEmpty()) {
      log.error("PurchaseOrder is not valid {}", dto);
      throw new InvalidEntityException("La commande fournisseur n'est pas valide", ErrorCodes.PURCHASE_ORDER_NOT_VALID, errors);
    }
    // Phase 5a : pre-check applicatif avant l'unique constraint SQL, qui remontait en 500 brut
    // (DataIntegrityViolationException, aucun code d'erreur) - voir docs/phase-5a-report.md.
    if (purchaseOrderRepository.findPurchaseOrderByCode(dto.getCode()).isPresent()) {
      log.error("PurchaseOrder code {} already exists", dto.getCode());
      throw new InvalidEntityException("Une commande fournisseur avec ce code existe deja", ErrorCodes.PURCHASE_ORDER_ALREADY_EXISTS);
    }

    PurchaseOrder purchaseOrder = PurchaseOrderDto.toEntity(dto);
    purchaseOrder.setOrderDate(Instant.now());
    PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

    lines.forEach(lineDto -> {
      PurchaseOrderLine line = PurchaseOrderLineDto.toNewEntity(lineDto);
      line.setPurchaseOrder(saved);
      purchaseOrderLineRepository.save(line);
    });

    return PurchaseOrderDto.fromEntity(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public PurchaseOrderDto findById(Long id) {
    return PurchaseOrderDto.fromEntity(fetchOrder(id));
  }

  @Override
  @Transactional(readOnly = true)
  public PurchaseOrderDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("PurchaseOrder CODE is null");
      return null;
    }
    return purchaseOrderRepository.findPurchaseOrderByCode(code)
        .map(PurchaseOrderDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune commande fournisseur avec le CODE = " + code + " n'a ete trouvee dans la BDD",
            ErrorCodes.PURCHASE_ORDER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<PurchaseOrderDto> findAll() {
    return purchaseOrderRepository.findAll().stream()
        .map(PurchaseOrderDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<PurchaseOrderLineDto> findLines(Long purchaseOrderId) {
    return purchaseOrderLineRepository.findAllByPurchaseOrderId(purchaseOrderId).stream()
        .map(PurchaseOrderLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<PurchaseOrderLineDto> findLinesByArticleId(Long articleId) {
    return purchaseOrderLineRepository.findAllByArticleId(articleId).stream()
        .map(PurchaseOrderLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public PurchaseOrderDto validate(Long id) {
    PurchaseOrder purchaseOrder = fetchOrder(id);
    purchaseOrder.validate();
    return PurchaseOrderDto.fromEntity(purchaseOrderRepository.save(purchaseOrder));
  }

  @Override
  public PurchaseOrderDto cancel(Long id) {
    PurchaseOrder purchaseOrder = fetchOrder(id);
    purchaseOrder.cancel();
    return PurchaseOrderDto.fromEntity(purchaseOrderRepository.save(purchaseOrder));
  }

  @Override
  public PurchaseOrderLineDto receiveLine(Long purchaseOrderId, Long purchaseOrderLineId, BigDecimal quantity, Long userId) {
    PurchaseOrder purchaseOrder = fetchOrder(purchaseOrderId);
    if (!authorizationService.hasPermission(userId, PURCHASE_ORDER_RECEIVE, ScopeType.SITE, purchaseOrder.getSite().getId())) {
      log.warn("User {} tried to receive purchase order {} on site {} without permission",
          userId, purchaseOrderId, purchaseOrder.getSite().getId());
      throw new InvalidOperationException(
          "Vous n'avez pas la permission de receptionner cette commande fournisseur",
          ErrorCodes.PURCHASE_ORDER_ACCESS_DENIED);
    }
    purchaseOrder.requireReceivable();

    PurchaseOrderLine line = purchaseOrderLineRepository.findById(purchaseOrderLineId)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune ligne de commande avec l'ID = " + purchaseOrderLineId + " n'a ete trouvee dans la BDD",
            ErrorCodes.PURCHASE_ORDER_LINE_NOT_FOUND));
    if (!line.getPurchaseOrder().getId().equals(purchaseOrderId)) {
      throw new InvalidOperationException(
          "Cette ligne n'appartient pas a la commande fournisseur " + purchaseOrderId,
          ErrorCodes.PURCHASE_ORDER_LINE_NOT_FOUND);
    }

    line.receive(quantity);
    purchaseOrderLineRepository.save(line);

    inventoryFacade.receive(line.getArticle().getId(), purchaseOrder.getSite().getId(), quantity,
        StockMovementSource.COMMANDE_FOURNISSEUR, purchaseOrder.getCode(), userId);

    boolean allLinesFullyReceived = purchaseOrderLineRepository.findAllByPurchaseOrderId(purchaseOrderId).stream()
        .allMatch(PurchaseOrderLine::isFullyReceived);
    if (allLinesFullyReceived) {
      purchaseOrder.markFullyReceived();
      purchaseOrderRepository.save(purchaseOrder);
    }

    return PurchaseOrderLineDto.fromEntity(line);
  }

  private PurchaseOrder fetchOrder(Long id) {
    if (id == null) {
      log.error("PurchaseOrder ID is null");
      throw new EntityNotFoundException("L'ID de la commande fournisseur est null", ErrorCodes.PURCHASE_ORDER_NOT_FOUND);
    }
    return purchaseOrderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune commande fournisseur avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.PURCHASE_ORDER_NOT_FOUND)
        );
  }
}
