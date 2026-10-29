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
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 5b-2c : findById/findAll/delete sont self-only (l'appelant ne voit/ne supprime que sa
 * propre organisation, resolue depuis son JWT) - voir docs/phase-5b2c-report.md.
 *
 * <p>Phase 5b-2e : {@code POST /organizations/create} a ete SUPPRIME. Une Organization creee par
 * cette route n'avait ni Tenant, ni utilisateur admin, ni affectation RBAC : une orpheline que
 * personne ne pouvait atteindre (son createur inclus, depuis que la lecture est scopee). C'etait
 * la cause racine de trois regressions de fixture consecutives (5b-2b, 5b-2c, 5b-2d). Le seul
 * chemin legitime de creation reste {@code POST /tenants/register}, qui orchestre
 * Tenant + Organization miroir + Site par defaut + admin + RBAC via
 * {@code TenantRegistrationServiceImpl} (lequel appelle {@code OrganizationService.save()}
 * directement, conserve intact). Meme raisonnement et meme precedent que la suppression de
 * {@code /entreprises/create} en Phase 4b - voir docs/phase-5b2e-report.md.
 */
@Tag(name = "organizations")
@RestController
public class OrganizationController {

  private OrganizationService organizationService;

  @Autowired
  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
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
