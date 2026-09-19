package com.kfokam48.gestiondestock.identity.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.application.PermissionService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
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

@Tag(name = "permissions")
@RestController
@Slf4j
public class PermissionController {

  private static final String RBAC_MANAGE = "RBAC_MANAGE";

  private PermissionService permissionService;
  private AuthorizationService authorizationService;

  @Autowired
  public PermissionController(PermissionService permissionService, AuthorizationService authorizationService) {
    this.permissionService = permissionService;
    this.authorizationService = authorizationService;
  }

  private void requireRbacManage(ExtendedUser principal) {
    Long userId = principal == null ? null : principal.getIdUtilisateur();
    Long organizationId = principal == null ? null : principal.getOrganizationId();
    if (!authorizationService.hasPermission(userId, RBAC_MANAGE, ScopeType.GLOBAL, null, organizationId)) {
      log.warn("User {} tried to manage permissions without RBAC_MANAGE on GLOBAL scope", userId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'administrer les permissions",
          ErrorCodes.PERMISSION_ACCESS_DENIED);
    }
  }

  @PostMapping(value = APP_ROOT + "/permissions/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public PermissionDto save(@RequestBody PermissionDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    return permissionService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/permissions/{idPermission}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PermissionDto findById(@PathVariable("idPermission") Long id) {
    return permissionService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/permissions/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PermissionDto> findAll() {
    return permissionService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/permissions/delete/{idPermission}")
  public void delete(@PathVariable("idPermission") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    requireRbacManage(principal);
    permissionService.delete(id);
  }
}
