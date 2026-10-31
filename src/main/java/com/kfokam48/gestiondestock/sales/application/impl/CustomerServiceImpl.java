package com.kfokam48.gestiondestock.sales.application.impl;

import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.sales.domain.model.Customer;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import com.kfokam48.gestiondestock.sales.application.validator.CustomerValidator;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrder;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderRepository;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerRepository;
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
public class CustomerServiceImpl implements CustomerService {

  private CustomerRepository customerRepository;
  private AuthorizationService authorizationService;
  private CustomerOrderRepository customerOrderRepository;

  @Autowired
  public CustomerServiceImpl(CustomerRepository customerRepository, CustomerOrderRepository customerOrderRepository,
      AuthorizationService authorizationService) {
    this.customerRepository = customerRepository;
    this.customerOrderRepository = customerOrderRepository;
    this.authorizationService = authorizationService;
  }

  @Override
  public CustomerDto save(CustomerDto dto, Long userId, Long organizationId) {
    requirePermission(userId, "CUSTOMER_CREATE", organizationId);
    // Phase 5b-2g : un id fourni par le client ferait de ce create un MERGE JPA, donc
    // l'ecrasement d'une ressource existante - voir docs/phase-5b2g-report.md.
    CreationIdGuard.requireNoId(dto.getId(), "d'un client");
    List<String> errors = CustomerValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Customer is not valid {}", dto);
      throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CUSTOMER_NOT_VALID, errors);
    }
    // Phase 5a : pre-check applicatif, exclut son propre id puisque save() est un upsert (mise a
    // jour d'un client existant y compris son propre mail sinon rejetee a tort) - voir
    // docs/phase-5a-report.md.
    if (StringUtils.hasLength(dto.getMail())) {
      customerRepository.findByMail(dto.getMail())
          .filter(existing -> !existing.getId().equals(dto.getId()))
          .ifPresent(existing -> {
            log.error("Customer mail {} already exists", dto.getMail());
            throw new InvalidEntityException("Un client avec ce mail existe deja", ErrorCodes.CUSTOMER_ALREADY_EXISTS);
          });
    }
    return CustomerDto.fromEntity(
        customerRepository.save(CustomerDto.toEntity(dto))
    );
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerDto findById(Long id) {
    if (id == null) {
      log.error("Customer ID is null");
      return null;
    }
    return customerRepository.findById(id)
        .map(CustomerDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.CUSTOMER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerDto findById(Long id, Long organizationId) {
    if (id == null) {
      log.error("Customer ID is null");
      return null;
    }
    return customerRepository.findById(id)
        .filter(customer -> belongsToOrganization(customer.getOrganizationId(), organizationId))
        .map(CustomerDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD",
            ErrorCodes.CUSTOMER_NOT_FOUND)
        );
  }

  @Override
  @Transactional(readOnly = true)
  public List<CustomerDto> findAll(Long organizationId) {
    return customerRepository.findAll().stream()
        .filter(customer -> belongsToOrganization(customer.getOrganizationId(), organizationId))
        .map(CustomerDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2a : les deux cotes doivent etre non-null pour matcher - voir docs/phase-5b2a-report.md.
  private boolean belongsToOrganization(Long entityOrganizationId, Long callerOrganizationId) {
    return entityOrganizationId != null && entityOrganizationId.equals(callerOrganizationId);
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Customer ID is null");
      return;
    }
    List<CustomerOrder> orders = customerOrderRepository.findAllByCustomerId(id);
    if (!orders.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer ce client qui est deja utilise",
          ErrorCodes.CUSTOMER_ALREADY_IN_USE);
    }
    customerRepository.deleteById(id);
  }

  // Phase 4c : remplace SaveClientPhoto (supprime, voir docs/phase-4c-report.md). save() est deja
  // un upsert plat, aucun risque de bug de mise a jour ici.
  @Override
  /**
   * Phase 5b-2g : mise a jour ciblee sur l'entite CHARGEE, et non un aller-retour par
   * {@code save(dto)}. Ce round-trip reinjectait l'id dans un chemin de creation - desormais
   * refuse par {@code CreationIdGuard} - et c'est exactement le motif de bug corrige en Phase 4c
   * sur {@code UserServiceImpl.updatePhoto} (le save() de l'epoque corrompait le mot de passe).
   * Voir docs/phase-5b2g-report.md.
   */
  // Phase 5b-2h : appartenance verifiee avant la mise a jour - voir docs/phase-5b2h-report.md.
  public CustomerDto updatePhoto(Long id, String url, Long organizationId) {
    findById(id, organizationId);
    Customer existing = customerRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CUSTOMER_NOT_FOUND));
    existing.setPhoto(url);
    return CustomerDto.fromEntity(customerRepository.save(existing));
  }

  // Phase 5b-2g : voir docs/phase-5b2g-report.md.
  @Override
  public CustomerDto update(Long id, CustomerDto dto, Long userId, Long organizationId) {
    Customer existing = customerRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CUSTOMER_NOT_FOUND));
    if (organizationId == null || existing.getOrganizationId() == null
        || !organizationId.equals(existing.getOrganizationId())) {
      log.warn("Customer {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CUSTOMER_NOT_FOUND);
    }
    requirePermission(userId, "CUSTOMER_UPDATE", organizationId);
    dto.setId(id);
    dto.setOrganizationId(existing.getOrganizationId());
    List<String> errors = CustomerValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Customer is not valid {}", dto);
      throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CUSTOMER_NOT_VALID, errors);
    }
    // Pre-check d'unicite : s'exclut lui-meme, sinon une mise a jour qui conserve son propre mail
    // serait rejetee a tort (meme motif qu'en Phase 5a, desormais porte par update et non save).
    if (StringUtils.hasLength(dto.getMail())) {
      customerRepository.findByMail(dto.getMail())
          .filter(other -> !other.getId().equals(id))
          .ifPresent(other -> {
            log.error("Customer mail {} already exists", dto.getMail());
            throw new InvalidEntityException("Un client avec ce mail existe deja", ErrorCodes.CUSTOMER_ALREADY_EXISTS);
          });
    }
    existing.setNom(dto.getNom());
    existing.setPrenom(dto.getPrenom());
    existing.setMail(dto.getMail());
    existing.setNumTel(dto.getNumTel());
    existing.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    return CustomerDto.fromEntity(customerRepository.save(existing));
  }
  // Phase 5b-2h : voir docs/phase-5b2h-report.md.
  @Override
  public void delete(Long id, Long userId, Long organizationId) {
    if (id == null) {
      log.error("Customer ID is null");
      return;
    }
    Customer existing = customerRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CUSTOMER_NOT_FOUND));
    Long ownerOrg = existing.getOrganizationId();
    if (organizationId == null || ownerOrg == null || !organizationId.equals(ownerOrg)) {
      log.warn("Customer {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucun client avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CUSTOMER_NOT_FOUND);
    }
    // Phase 5c : appartenance (ci-dessus) -> permission -> ecriture.
    requirePermission(userId, "CUSTOMER_DELETE", organizationId);
    delete(id);
  }
  /**
   * Phase 5c : ordre strict appartenance -> permission -> ecriture. L'appartenance est verifiee
   * en amont (5b-2g/2h) et renvoie un *_NOT_FOUND masque ; ici on repond a la question distincte
   * « qui, DANS l'organisation, a le droit de faire cela » - voir docs/rbac-matrix.md.
   */
  private void requirePermission(Long userId, String permissionCode, Long organizationId) {
    if (!authorizationService.hasPermission(userId, permissionCode, ScopeType.ORGANIZATION,
        organizationId, organizationId)) {
      log.warn("User {} lacks {} in organization {}", userId, permissionCode, organizationId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action",
          ErrorCodes.CUSTOMER_ACCESS_DENIED);
    }
  }
}
