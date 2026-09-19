package com.kfokam48.gestiondestock.sales.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import com.kfokam48.gestiondestock.sales.application.validator.CustomerOrderValidator;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrder;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderLine;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderStatus;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderLineRepository;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderRepository;
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
public class CustomerOrderServiceImpl implements CustomerOrderService {

  private static final String CUSTOMER_ORDER_RESERVE = "CUSTOMER_ORDER_RESERVE";
  private static final String CUSTOMER_ORDER_DELIVER = "CUSTOMER_ORDER_DELIVER";
  private static final String CUSTOMER_ORDER_CANCEL = "CUSTOMER_ORDER_CANCEL";

  private CustomerOrderRepository customerOrderRepository;
  private CustomerOrderLineRepository customerOrderLineRepository;
  private InventoryFacade inventoryFacade;
  private AuthorizationService authorizationService;

  @Autowired
  public CustomerOrderServiceImpl(CustomerOrderRepository customerOrderRepository,
      CustomerOrderLineRepository customerOrderLineRepository, InventoryFacade inventoryFacade,
      AuthorizationService authorizationService) {
    this.customerOrderRepository = customerOrderRepository;
    this.customerOrderLineRepository = customerOrderLineRepository;
    this.inventoryFacade = inventoryFacade;
    this.authorizationService = authorizationService;
  }

  private void requireOrderPermission(String permissionCode, CustomerOrder order, Long userId) {
    if (!authorizationService.hasPermission(userId, permissionCode, ScopeType.SITE, order.getSite().getId())) {
      log.warn("User {} tried to {} on customer order {} (site {}) without permission",
          userId, permissionCode, order.getId(), order.getSite().getId());
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action sur cette commande client",
          ErrorCodes.CUSTOMER_ORDER_ACCESS_DENIED);
    }
  }

  @Override
  public CustomerOrderDto create(CustomerOrderDto dto, List<CustomerOrderLineDto> lines) {
    List<String> errors = CustomerOrderValidator.validate(dto, lines);
    if (!errors.isEmpty()) {
      log.error("CustomerOrder is not valid {}", dto);
      throw new InvalidEntityException("La commande client n'est pas valide", ErrorCodes.CUSTOMER_ORDER_NOT_VALID, errors);
    }
    // Phase 5a : pre-check applicatif avant l'unique constraint SQL, qui remontait en 500 brut
    // (DataIntegrityViolationException, aucun code d'erreur) - voir docs/phase-5a-report.md.
    if (customerOrderRepository.findCustomerOrderByCode(dto.getCode()).isPresent()) {
      log.error("CustomerOrder code {} already exists", dto.getCode());
      throw new InvalidEntityException("Une commande client avec ce code existe deja", ErrorCodes.CUSTOMER_ORDER_ALREADY_EXISTS);
    }

    CustomerOrder order = CustomerOrderDto.toEntity(dto);
    order.setOrderDate(Instant.now());
    CustomerOrder saved = customerOrderRepository.save(order);

    lines.forEach(lineDto -> {
      CustomerOrderLine line = CustomerOrderLineDto.toNewEntity(lineDto);
      line.setCustomerOrder(saved);
      customerOrderLineRepository.save(line);
    });

    return CustomerOrderDto.fromEntity(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerOrderDto findById(Long id) {
    return CustomerOrderDto.fromEntity(fetchOrder(id));
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerOrderDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("CustomerOrder CODE is null");
      return null;
    }
    return customerOrderRepository.findCustomerOrderByCode(code)
        .map(CustomerOrderDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune commande client avec le CODE = " + code + " n'a ete trouvee dans la BDD",
            ErrorCodes.CUSTOMER_ORDER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerOrderDto> findAll() {
    return customerOrderRepository.findAll().stream()
        .map(CustomerOrderDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerOrderLineDto> findLines(Long customerOrderId) {
    return customerOrderLineRepository.findAllByCustomerOrderId(customerOrderId).stream()
        .map(CustomerOrderLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<CustomerOrderLineDto> findLinesByArticleId(Long articleId) {
    return customerOrderLineRepository.findAllByArticleId(articleId).stream()
        .map(CustomerOrderLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public CustomerOrderDto validate(Long id) {
    CustomerOrder order = fetchOrder(id);
    order.validate();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  @Override
  public CustomerOrderDto reserve(Long id, Long userId) {
    CustomerOrder order = fetchOrder(id);
    requireOrderPermission(CUSTOMER_ORDER_RESERVE, order, userId);
    order.requireReservable();

    List<CustomerOrderLine> lines = customerOrderLineRepository.findAllByCustomerOrderId(id);
    for (CustomerOrderLine line : lines) {
      inventoryFacade.reserve(line.getArticle().getId(), order.getSite().getId(), line.getQuantite(),
          StockMovementSource.COMMANDE_CLIENT, order.getCode(), userId);
    }

    order.markReserved();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  @Override
  public CustomerOrderDto prepare(Long id) {
    CustomerOrder order = fetchOrder(id);
    order.requirePreparable();
    order.markPrepared();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  @Override
  public CustomerOrderDto ship(Long id) {
    CustomerOrder order = fetchOrder(id);
    order.requireShippable();
    order.markShipped();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  @Override
  public CustomerOrderDto deliver(Long id, Long userId) {
    CustomerOrder order = fetchOrder(id);
    requireOrderPermission(CUSTOMER_ORDER_DELIVER, order, userId);
    order.requireDeliverable();

    List<CustomerOrderLine> lines = customerOrderLineRepository.findAllByCustomerOrderId(id);
    for (CustomerOrderLine line : lines) {
      inventoryFacade.releaseReservation(line.getArticle().getId(), order.getSite().getId(), line.getQuantite(),
          StockMovementSource.COMMANDE_CLIENT, order.getCode(), userId);
      inventoryFacade.issue(line.getArticle().getId(), order.getSite().getId(), line.getQuantite(),
          StockMovementSource.COMMANDE_CLIENT, order.getCode(), userId);
    }

    order.markDelivered();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  @Override
  public CustomerOrderDto cancel(Long id, Long userId) {
    CustomerOrder order = fetchOrder(id);
    requireOrderPermission(CUSTOMER_ORDER_CANCEL, order, userId);

    if (order.getStatus() == CustomerOrderStatus.RESERVEE || order.getStatus() == CustomerOrderStatus.PREPAREE) {
      List<CustomerOrderLine> lines = customerOrderLineRepository.findAllByCustomerOrderId(id);
      for (CustomerOrderLine line : lines) {
        inventoryFacade.releaseReservation(line.getArticle().getId(), order.getSite().getId(), line.getQuantite(),
            StockMovementSource.COMMANDE_CLIENT, order.getCode(), userId);
      }
    }

    order.cancel();
    return CustomerOrderDto.fromEntity(customerOrderRepository.save(order));
  }

  private CustomerOrder fetchOrder(Long id) {
    if (id == null) {
      log.error("CustomerOrder ID is null");
      throw new EntityNotFoundException("L'ID de la commande client est null", ErrorCodes.CUSTOMER_ORDER_NOT_FOUND);
    }
    return customerOrderRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune commande client avec l'ID = " + id + " n'a ete trouvee dans la BDD",
            ErrorCodes.CUSTOMER_ORDER_NOT_FOUND)
        );
  }
}
