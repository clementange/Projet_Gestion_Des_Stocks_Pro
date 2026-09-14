package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.dto.ClientDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import com.kfokam48.gestiondestock.services.ClientService;
import com.kfokam48.gestiondestock.validator.ClientValidator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Phase 18 : /clients/* garde son contrat HTTP (ClientDto) mais est desormais re-backe par
// sales.Customer (Phase 16) au lieu de la table legacy client.
@Service
@Slf4j
public class ClientServiceImpl implements ClientService {

  private CustomerService customerService;
  private EntrepriseRepository entrepriseRepository;

  @Autowired
  public ClientServiceImpl(CustomerService customerService, EntrepriseRepository entrepriseRepository) {
    this.customerService = customerService;
    this.entrepriseRepository = entrepriseRepository;
  }

  @Override
  public ClientDto save(ClientDto dto) {
    List<String> errors = ClientValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Client is not valid {}", dto);
      throw new InvalidEntityException("Le client n'est pas valide", ErrorCodes.CLIENT_NOT_VALID, errors);
    }

    CustomerDto saved = customerService.save(toCustomerDto(dto));
    return toClientDto(saved, dto.getIdEntreprise());
  }

  @Override
  public ClientDto findById(Long id) {
    if (id == null) {
      log.error("Client ID is null");
      return null;
    }
    return toClientDto(customerService.findById(id), null);
  }

  @Override
  public List<ClientDto> findAll() {
    return customerService.findAll().stream()
        .map(customer -> toClientDto(customer, null))
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Client ID is null");
      return;
    }
    customerService.delete(id);
  }

  private CustomerDto toCustomerDto(ClientDto dto) {
    return CustomerDto.builder()
        .id(dto.getId())
        .nom(dto.getNom())
        .prenom(dto.getPrenom())
        .adresse(dto.getAdresse())
        .photo(dto.getPhoto())
        .mail(dto.getMail())
        .numTel(dto.getNumTel())
        .organizationId(resolveOrganizationId(dto.getIdEntreprise()))
        .build();
  }

  private ClientDto toClientDto(CustomerDto customer, Long fallbackIdEntreprise) {
    if (customer == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null
        ? fallbackIdEntreprise
        : resolveIdEntreprise(customer.getOrganizationId());
    return ClientDto.builder()
        .id(customer.getId())
        .nom(customer.getNom())
        .prenom(customer.getPrenom())
        .adresse(customer.getAdresse())
        .photo(customer.getPhoto())
        .mail(customer.getMail())
        .numTel(customer.getNumTel())
        .idEntreprise(idEntreprise)
        .build();
  }

  private Long resolveOrganizationId(Long idEntreprise) {
    if (idEntreprise == null) {
      return null;
    }
    return entrepriseRepository.findById(idEntreprise).map(Entreprise::getOrganizationId).orElse(null);
  }

  private Long resolveIdEntreprise(Long organizationId) {
    if (organizationId == null) {
      return null;
    }
    return entrepriseRepository.findByOrganizationId(organizationId).map(Entreprise::getId).orElse(null);
  }
}
