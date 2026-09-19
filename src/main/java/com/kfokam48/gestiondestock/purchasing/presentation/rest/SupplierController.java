package com.kfokam48.gestiondestock.purchasing.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4d : contrat canonique pour les fournisseurs (successeur de {@code /fournisseurs/*},
 * supprime - voir docs/phase-4d-report.md). N'existait pas avant cet increment :
 * {@code purchasing.Supplier} n'etait expose que via l'adaptateur legacy
 * {@code FournisseurController}.
 */
@Tag(name = "suppliers")
@RestController
public class SupplierController {

  private SupplierService supplierService;

  @Autowired
  public SupplierController(SupplierService supplierService) {
    this.supplierService = supplierService;
  }

  // Phase 5b-2a : organizationId force depuis l'appelant, jamais depuis le corps de la requete -
  // voir CustomerController.create et docs/phase-5b2a-report.md.
  @PostMapping(value = APP_ROOT + "/suppliers/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public SupplierDto create(@RequestBody SupplierDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    dto.setOrganizationId(principal.getOrganizationId());
    return supplierService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/suppliers/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SupplierDto findById(@PathVariable("id") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return supplierService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/suppliers/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SupplierDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return supplierService.findAll(principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/suppliers/delete/{id}")
  public void delete(@PathVariable("id") Long id) {
    supplierService.delete(id);
  }
}
