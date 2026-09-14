package com.kfokam48.gestiondestock.services.impl;

import com.kfokam48.gestiondestock.dto.FournisseurDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.model.Entreprise;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import com.kfokam48.gestiondestock.repository.EntrepriseRepository;
import com.kfokam48.gestiondestock.services.FournisseurService;
import com.kfokam48.gestiondestock.validator.FournisseurValidator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Phase 18 : /fournisseurs/* garde son contrat HTTP (FournisseurDto) mais est desormais re-backe
// par purchasing.Supplier (Phase 16) au lieu de la table legacy fournisseur.
@Service
@Slf4j
public class FournisseurServiceImpl implements FournisseurService {

  private SupplierService supplierService;
  private EntrepriseRepository entrepriseRepository;

  @Autowired
  public FournisseurServiceImpl(SupplierService supplierService, EntrepriseRepository entrepriseRepository) {
    this.supplierService = supplierService;
    this.entrepriseRepository = entrepriseRepository;
  }

  @Override
  public FournisseurDto save(FournisseurDto dto) {
    List<String> errors = FournisseurValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Fournisseur is not valid {}", dto);
      throw new InvalidEntityException("Le fournisseur n'est pas valide", ErrorCodes.FOURNISSEUR_NOT_VALID, errors);
    }

    SupplierDto saved = supplierService.save(toSupplierDto(dto));
    return toFournisseurDto(saved, dto.getIdEntreprise());
  }

  @Override
  public FournisseurDto findById(Long id) {
    if (id == null) {
      log.error("Fournisseur ID is null");
      return null;
    }
    return toFournisseurDto(supplierService.findById(id), null);
  }

  @Override
  public List<FournisseurDto> findAll() {
    return supplierService.findAll().stream()
        .map(supplier -> toFournisseurDto(supplier, null))
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Fournisseur ID is null");
      return;
    }
    supplierService.delete(id);
  }

  private SupplierDto toSupplierDto(FournisseurDto dto) {
    return SupplierDto.builder()
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

  private FournisseurDto toFournisseurDto(SupplierDto supplier, Long fallbackIdEntreprise) {
    if (supplier == null) {
      return null;
    }
    Long idEntreprise = fallbackIdEntreprise != null
        ? fallbackIdEntreprise
        : resolveIdEntreprise(supplier.getOrganizationId());
    return FournisseurDto.builder()
        .id(supplier.getId())
        .nom(supplier.getNom())
        .prenom(supplier.getPrenom())
        .adresse(supplier.getAdresse())
        .photo(supplier.getPhoto())
        .mail(supplier.getMail())
        .numTel(supplier.getNumTel())
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
