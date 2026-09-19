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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

  @PostMapping(value = APP_ROOT + "/roles/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public RoleDto save(@RequestBody RoleDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    return roleService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/roles/{idRole}", produces = MediaType.APPLICATION_JSON_VALUE)
  public RoleDto findById(@PathVariable("idRole") Long id) {
    return roleService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/roles/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<RoleDto> findAll() {
    return roleService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/roles/delete/{idRole}")
  public void delete(@PathVariable("idRole") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    roleService.delete(id);
  }
}
