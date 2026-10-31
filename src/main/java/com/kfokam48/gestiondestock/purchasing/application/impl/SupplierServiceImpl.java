package com.kfokam48.gestiondestock.purchasing.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.Supplier;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
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
    // Phase 5b-2g : un id fourni par le client ferait de ce create un MERGE JPA, donc
    // l'ecrasement d'une ressource existante - voir docs/phase-5b2g-report.md.
    CreationIdGuard.requireNoId(dto.getId(), "d'un fournisseur");
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
  public SupplierDto findById(Long id, Long organizationId) {
    if (id == null) {
      log.error("Supplier ID is null");
      return null;
    }
    return supplierRepository.findById(id)
        .filter(supplier -> belongsToOrganization(supplier.getOrganizationId(), organizationId))
        .map(SupplierDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.SUPPLIER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<SupplierDto> findAll(Long organizationId) {
    return supplierRepository.findAll().stream()
        .filter(supplier -> belongsToOrganization(supplier.getOrganizationId(), organizationId))
        .map(SupplierDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2a : les deux cotes doivent etre non-null pour matcher - voir docs/phase-5b2a-report.md.
  private boolean belongsToOrganization(Long entityOrganizationId, Long callerOrganizationId) {
    return entityOrganizationId != null && entityOrganizationId.equals(callerOrganizationId);
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
  /**
   * Phase 5b-2g : mise a jour ciblee sur l'entite CHARGEE, et non un aller-retour par
   * {@code save(dto)}. Ce round-trip reinjectait l'id dans un chemin de creation - desormais
   * refuse par {@code CreationIdGuard} - et c'est exactement le motif de bug corrige en Phase 4c
   * sur {@code UserServiceImpl.updatePhoto} (le save() de l'epoque corrompait le mot de passe).
   * Voir docs/phase-5b2g-report.md.
   */
  // Phase 5b-2h : appartenance verifiee avant la mise a jour - voir docs/phase-5b2h-report.md.
  public SupplierDto updatePhoto(Long id, String url, Long organizationId) {
    findById(id, organizationId);
    Supplier existing = supplierRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.SUPPLIER_NOT_FOUND));
    existing.setPhoto(url);
    return SupplierDto.fromEntity(supplierRepository.save(existing));
  }

  // Phase 5b-2g : voir docs/phase-5b2g-report.md.
  @Override
  public SupplierDto update(Long id, SupplierDto dto, Long organizationId) {
    Supplier existing = supplierRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.SUPPLIER_NOT_FOUND));
    if (organizationId == null || existing.getOrganizationId() == null
        || !organizationId.equals(existing.getOrganizationId())) {
      log.warn("Supplier {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.SUPPLIER_NOT_FOUND);
    }
    dto.setId(id);
    dto.setOrganizationId(existing.getOrganizationId());
    List<String> errors = SupplierValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Supplier is not valid {}", dto);
      throw new InvalidEntityException("Le fournisseur n'est pas valide", ErrorCodes.SUPPLIER_NOT_VALID, errors);
    }
    if (StringUtils.hasLength(dto.getMail())) {
      supplierRepository.findByMail(dto.getMail())
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            log.error("Supplier mail {} already exists", dto.getMail());
            throw new InvalidEntityException("Un fournisseur avec ce mail existe deja", ErrorCodes.SUPPLIER_ALREADY_EXISTS);
          });
    }
    existing.setNom(dto.getNom());
    existing.setPrenom(dto.getPrenom());
    existing.setMail(dto.getMail());
    existing.setNumTel(dto.getNumTel());
    existing.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    return SupplierDto.fromEntity(supplierRepository.save(existing));
  }
  // Phase 5b-2h : voir docs/phase-5b2h-report.md.
  @Override
  public void delete(Long id, Long organizationId) {
    if (id == null) {
      log.error("Supplier ID is null");
      return;
    }
    Supplier existing = supplierRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.SUPPLIER_NOT_FOUND));
    Long ownerOrg = existing.getOrganizationId();
    if (organizationId == null || ownerOrg == null || !organizationId.equals(ownerOrg)) {
      log.warn("Supplier {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucun fournisseur avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.SUPPLIER_NOT_FOUND);
    }
    delete(id);
  }
}
