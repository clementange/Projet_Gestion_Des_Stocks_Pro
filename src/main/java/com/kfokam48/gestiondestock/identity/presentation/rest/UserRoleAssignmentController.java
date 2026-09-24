package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "user-role-assignments")
@RestController
@Slf4j
public class UserRoleAssignmentController {

  private static final String RBAC_MANAGE = "RBAC_MANAGE";

  private UserRoleAssignmentService userRoleAssignmentService;
  private AuthorizationService authorizationService;

  @Autowired
  public UserRoleAssignmentController(UserRoleAssignmentService userRoleAssignmentService,
      AuthorizationService authorizationService) {
    this.userRoleAssignmentService = userRoleAssignmentService;
    this.authorizationService = authorizationService;
  }

  private void requireRbacManage(ExtendedUser principal) {
    Long userId = principal == null ? null : principal.getIdUtilisateur();
    Long organizationId = principal == null ? null : principal.getOrganizationId();
    if (!authorizationService.hasPermission(userId, RBAC_MANAGE, ScopeType.GLOBAL, null, organizationId)) {
      log.warn("User {} tried to manage RBAC without RBAC_MANAGE on GLOBAL scope", userId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'administrer les affectations de roles",
          ErrorCodes.USER_ROLE_ASSIGNMENT_ACCESS_DENIED);
    }
  }

  @PostMapping(value = APP_ROOT + "/user-role-assignments/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public UserRoleAssignmentDto save(@RequestBody UserRoleAssignmentDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    return userRoleAssignmentService.save(dto);
  }

  /**
   * Phase 5b-2c : l'appartenance de l'affectation a l'organisation de l'appelant est verifiee
   * EN PREMIER, avant requireRbacManage - garde-fou independant, non contournable par la logique
   * RBAC elle-meme (meme ordre que requireSiteInOrganization avant hasPermission en 5b-2b). Un
   * appelant GLOBAL RBAC_MANAGE dans son organisation ne doit jamais pouvoir lire l'affectation
   * d'une AUTRE organisation simplement parce que hasPermission le laisserait passer - voir
   * docs/phase-5b2c-report.md.
   */
  @GetMapping(value = APP_ROOT + "/user-role-assignments/{idAssignment}", produces = MediaType.APPLICATION_JSON_VALUE)
  public UserRoleAssignmentDto findById(@PathVariable("idAssignment") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    UserRoleAssignmentDto dto = userRoleAssignmentService.findById(id, principal.getOrganizationId());
    requireRbacManage(principal);
    return dto;
  }

  @GetMapping(value = APP_ROOT + "/user-role-assignments/filter/user/{idUser}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<UserRoleAssignmentDto> findAllByUser(@PathVariable("idUser") Long idUser, @AuthenticationPrincipal ExtendedUser principal) {
    List<UserRoleAssignmentDto> assignments = userRoleAssignmentService.findAllByUser(idUser, principal.getOrganizationId());
    requireRbacManage(principal);
    return assignments;
  }

  @DeleteMapping(value = APP_ROOT + "/user-role-assignments/delete/{idAssignment}")
  public void delete(@PathVariable("idAssignment") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    userRoleAssignmentService.delete(id);
  }
}
