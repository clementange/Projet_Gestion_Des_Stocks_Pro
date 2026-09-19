package com.kfokam48.gestiondestock.purchasing.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import com.kfokam48.gestiondestock.purchasing.application.validator.SupplierValidator;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrder;
import com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.PurchaseOrderRepository;
import com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.SupplierRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Transactional
@Service
@Slf4j
public class SupplierServiceImpl implements SupplierService {

  private SupplierRepository supplierRepository;
  private PurchaseOrderRepository purchaseOrderRepository;

  @Autowired
  public SupplierServiceImpl(SupplierRepository supplierRepository, PurchaseOrderRepository purchaseOrderRepository) {
    this.supplierRepository = supplierRepository;
    this.purchaseOrderRepository = purchaseOrderRepository;
  }

  @Override
  public SupplierDto save(SupplierDto dto) {
    List<String> errors = SupplierValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Supplier is not valid {}", dto);
      throw new InvalidEntityException("Le fournisseur n'est pas valide", ErrorCodes.SUPPLIER_NOT_VALID, errors);
    }
    // Phase 5a : pre-check applicatif, exclut son propre id puisque save() est un upsert (mise a
    // jour d'un fournisseur existant y compris son propre mail sinon rejetee a tort) - voir
    // docs/phase-5a-report.md.
    if (StringUtils.hasLength(dto.getMail())) {
      supplierRepository.findByMail(dto.getMail())
          .filter(existing -> !existing.getId().equals(dto.getId()))
          .ifPresent(existing -> {
            log.error("Supplier mail {} already exists", dto.getMail());
            throw new InvalidEntityException("Un fournisseur avec ce mail existe deja", ErrorCodes.SUPPLIER_ALREADY_EXISTS);
          });
    }
    return SupplierDto.fromEntity(
        supplierRepository.save(SupplierDto.toEntity(dto))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public SupplierDto findById(Long id) {
    if (id == null) {
      log.error("Supplier ID is null");
      return null;
    }
    return supplierRepository.findById(id)
        .map(SupplierDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.SUPPLIER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<SupplierDto> findAll() {
    return supplierRepository.findAll().stream()
        .map(SupplierDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Supplier ID is null");
      return;
    }
    List<PurchaseOrder> orders = purchaseOrderRepository.findAllBySupplierId(id);
    if (!orders.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer ce fournisseur qui est deja utilise",
          ErrorCodes.SUPPLIER_ALREADY_IN_USE);
    }
    supplierRepository.deleteById(id);
  }

  // Phase 4c : remplace SaveFournisseurPhoto (supprime, voir docs/phase-4c-report.md). save() est
  // deja un upsert plat, aucun risque de bug de mise a jour ici.
  @Override
  public SupplierDto updatePhoto(Long id, String url) {
    SupplierDto supplier = findById(id);
    supplier.setPhoto(url);
    return save(supplier);
  }
}
