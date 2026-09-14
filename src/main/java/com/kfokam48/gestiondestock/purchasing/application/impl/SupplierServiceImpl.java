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
}
