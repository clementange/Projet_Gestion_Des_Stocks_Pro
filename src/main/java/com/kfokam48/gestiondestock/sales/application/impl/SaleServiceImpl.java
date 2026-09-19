package com.kfokam48.gestiondestock.sales.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import com.kfokam48.gestiondestock.sales.application.validator.SaleValidator;
import com.kfokam48.gestiondestock.sales.domain.model.Sale;
import com.kfokam48.gestiondestock.sales.domain.model.SaleLine;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.SaleLineRepository;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.SaleRepository;
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
public class SaleServiceImpl implements SaleService {

  private static final String SALE_CREATE = "SALE_CREATE";

  private SaleRepository saleRepository;
  private SaleLineRepository saleLineRepository;
  private InventoryFacade inventoryFacade;
  private AuthorizationService authorizationService;

  @Autowired
  public SaleServiceImpl(SaleRepository saleRepository, SaleLineRepository saleLineRepository,
      InventoryFacade inventoryFacade, AuthorizationService authorizationService) {
    this.saleRepository = saleRepository;
    this.saleLineRepository = saleLineRepository;
    this.inventoryFacade = inventoryFacade;
    this.authorizationService = authorizationService;
  }

  @Override
  public SaleDto create(SaleDto dto, List<SaleLineDto> lines, Long userId) {
    List<String> errors = SaleValidator.validate(dto, lines);
    if (!errors.isEmpty()) {
      log.error("Sale is not valid {}", dto);
      throw new InvalidEntityException("La vente n'est pas valide", ErrorCodes.SALE_NOT_VALID, errors);
    }
    // Phase 5a : pre-check applicatif avant l'unique constraint SQL, qui remontait en 500 brut
    // (DataIntegrityViolationException, aucun code d'erreur) - voir docs/phase-5a-report.md.
    if (saleRepository.findSaleByCode(dto.getCode()).isPresent()) {
      log.error("Sale code {} already exists", dto.getCode());
      throw new InvalidEntityException("Une vente avec ce code existe deja", ErrorCodes.SALE_ALREADY_EXISTS);
    }
    if (!authorizationService.hasPermission(userId, SALE_CREATE, ScopeType.SITE, dto.getSite().getId())) {
      log.warn("User {} tried to create a sale on site {} without SALE_CREATE on that scope", userId, dto.getSite().getId());
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'enregistrer une vente sur ce site",
          ErrorCodes.SALE_ACCESS_DENIED);
    }

    Sale sale = SaleDto.toEntity(dto);
    sale.setSaleDate(Instant.now());
    Sale savedSale = saleRepository.save(sale);

    // Chaque ligne sort immediatement le stock du site vendeur. Si une seule ligne echoue
    // (stock insuffisant), l'exception remonte et @Transactional annule toute la vente : pas de
    // vente partiellement enregistree ni de stock partiellement sorti.
    lines.forEach(lineDto -> {
      SaleLine line = SaleLineDto.toNewEntity(lineDto);
      line.setSale(savedSale);
      saleLineRepository.save(line);

      inventoryFacade.issue(line.getArticle().getId(), savedSale.getSite().getId(), line.getQuantite(),
          StockMovementSource.VENTE, savedSale.getCode(), userId);
    });

    return SaleDto.fromEntity(savedSale);
  }

  @Override
  @Transactional(readOnly = true)
  public SaleDto findById(Long id) {
    if (id == null) {
      log.error("Sale ID is null");
      return null;
    }
    return saleRepository.findById(id)
        .map(SaleDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune vente avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.SALE_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public SaleDto findByCode(String code) {
    if (!StringUtils.hasLength(code)) {
      log.error("Sale CODE is null");
      return null;
    }
    return saleRepository.findSaleByCode(code)
        .map(SaleDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune vente avec le CODE = " + code + " n'a ete trouvee dans la BDD", ErrorCodes.SALE_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<SaleDto> findAll() {
    return saleRepository.findAll().stream()
        .map(SaleDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SaleLineDto> findLines(Long saleId) {
    return saleLineRepository.findAllBySaleId(saleId).stream()
        .map(SaleLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<SaleLineDto> findLinesByArticleId(Long articleId) {
    return saleLineRepository.findAllByArticleId(articleId).stream()
        .map(SaleLineDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional(readOnly = true)
  public List<SaleDto> findAllBySiteAndPeriod(Long siteId, Instant from, Instant to) {
    return saleRepository.findAllBySiteIdAndSaleDateBetween(siteId, from, to).stream()
        .map(SaleDto::fromEntity)
        .collect(Collectors.toList());
  }
}
