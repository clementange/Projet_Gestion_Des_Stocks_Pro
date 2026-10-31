package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 5b-2f : {@code POST /roles/create} a ete SUPPRIME. Role est un catalogue
 * GLOBAL partage par tous les tenants (aucun {@code organizationId} - confirme en 5b-2c), seede
 * par Flyway (V3/V4). Le gate {@code RBAC_MANAGE} ne protegeait rien ici, puisque tout admin de
 * tenant le possede via le bootstrap ADMINISTRATEUR : n'importe lequel pouvait donc creer - et,
 * via un id fourni, ECRASER - une entree utilisee par tous les autres tenants. Meme precedent que
 * la suppression de {@code /organizations/create} en 5b-2e. {@code RoleService.save()}
 * est conserve : il reste utilise par les tests et resterait necessaire a tout futur amorcage -
 * voir docs/phase-5b2f-report.md.
 *
 * <p>Phase 5b-2h : {@code DELETE /roles/delete/{id}} est SUPPRIME pour la meme raison que la
 * creation - supprimer une entree d'un catalogue GLOBAL impacte tous les tenants, et le gate
 * RBAC_MANAGE ne distingue pas les tenants puisque chaque admin le possede - voir
 * docs/phase-5b2h-report.md.
 */
@Tag(name = "roles")
@RestController
@Slf4j
public class RoleController {

  private static final String RBAC_MANAGE = "RBAC_MANAGE";

  private RoleService roleService;
  private AuthorizationService authorizationService;

  @Autowired
  public RoleController(RoleService roleService, AuthorizationService authorizationService) {
    this.roleService = roleService;
    this.authorizationService = authorizationService;
  }

  private void requireRbacManage(ExtendedUser principal) {
    Long userId = principal == null ? null : principal.getIdUtilisateur();
    Long organizationId = principal == null ? null : principal.getOrganizationId();
    if (!authorizationService.hasPermission(userId, RBAC_MANAGE, ScopeType.GLOBAL, null, organizationId)) {
      log.warn("User {} tried to manage roles without RBAC_MANAGE on GLOBAL scope", userId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'administrer les roles",
          ErrorCodes.ROLE_ACCESS_DENIED);
    }
  }


  @GetMapping(value = APP_ROOT + "/roles/{idRole}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RoleDto findById(@PathVariable("idRole") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    return roleService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/roles/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RoleDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    return roleService.findAll();
  }

}
