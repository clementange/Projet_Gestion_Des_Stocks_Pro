package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
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
 * Phase 5b-2c : findById/findAll/delete sont self-only (l'appelant ne voit/ne supprime que sa
 * propre organisation, resolue depuis son JWT). save() (creation) reste sans verification -
 * delibere, hors perimetre de cet increment (gap RBAC-on-create deja catalogue) - voir
 * docs/phase-5b2c-report.md.
 */
@Tag(name = "organizations")
@RestController
public class OrganizationController {

  private OrganizationService organizationService;

  @Autowired
  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @PostMapping(value = APP_ROOT + "/organizations/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public OrganizationDto save(@RequestBody OrganizationDto dto) {
    return organizationService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/organizations/{idOrganization}", produces = MediaType.APPLICATION_JSON_VALUE)
  public OrganizationDto findById(@PathVariable("idOrganization") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return organizationService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/organizations/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<OrganizationDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return organizationService.findAll(principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/organizations/delete/{idOrganization}")
  public void delete(@PathVariable("idOrganization") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    organizationService.delete(id, principal.getOrganizationId());
  }
}
