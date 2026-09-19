package com.kfokam48.gestiondestock.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.identity.application.PermissionService;
import com.kfokam48.gestiondestock.identity.application.RoleService;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.organization.application.OrganizationService;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Reproduit les tests 4 et 5 de la section 43 du prompt maitre : un responsable d'entrepot a
 * acces a son entrepot et pas a un autre, un Directeur General a un acces global.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AuthorizationServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PermissionService permissionService;

  @Autowired
  private RoleService roleService;

  @Autowired
  private UserRoleAssignmentService userRoleAssignmentService;

  @Autowired
  private AuthorizationService authorizationService;

  @Autowired
  private OrganizationService organizationService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private Long createOrganization(String name) {
    return organizationService.save(OrganizationDto.builder().name(name).active(true).build()).getId();
  }

  @Test
  public void warehouseManagerShouldOnlyAccessTheirOwnWarehouse() {
    PermissionDto stockView = permissionService.save(
        PermissionDto.builder().code(uniqueCode("STOCK_VIEW")).description("Consulter le stock").build());

    RoleDto warehouseManager = roleService.save(
        RoleDto.builder().code(uniqueCode("WAREHOUSE_MANAGER")).name("Responsable entrepot")
            .permissions(Set.of(stockView)).build());

    Long userId = 1001L;
    Long organizationId = createOrganization("Societe Warehouse Manager");
    Long warehouseAId = 5001L;
    Long warehouseBId = 5002L;

    userRoleAssignmentService.save(
        UserRoleAssignmentDto.builder().userId(userId).role(warehouseManager)
            .scopeType(ScopeType.WAREHOUSE).scopeId(warehouseAId).organizationId(organizationId).build());

    assertTrue(authorizationService.hasPermission(userId, stockView.getCode(), ScopeType.WAREHOUSE, warehouseAId, organizationId));
    assertFalse(authorizationService.hasPermission(userId, stockView.getCode(), ScopeType.WAREHOUSE, warehouseBId, organizationId));
    assertFalse(authorizationService.hasGlobalAccess(userId, organizationId));
  }

  @Test
  public void directeurGeneralShouldHaveGlobalAccess() {
    PermissionDto stockAdjust = permissionService.save(
        PermissionDto.builder().code(uniqueCode("STOCK_ADJUST")).description("Ajuster le stock").build());

    RoleDto directeurGeneral = roleService.save(
        RoleDto.builder().code(uniqueCode("DIRECTEUR_GENERAL")).name("Directeur General")
            .permissions(Set.of(stockAdjust)).build());

    Long userId = 2002L;
    Long organizationId = createOrganization("Societe Directeur General");

    userRoleAssignmentService.save(
        UserRoleAssignmentDto.builder().userId(userId).role(directeurGeneral)
            .scopeType(ScopeType.GLOBAL).organizationId(organizationId).build());

    assertTrue(authorizationService.hasGlobalAccess(userId, organizationId));
    assertTrue(authorizationService.hasPermission(userId, stockAdjust.getCode(), ScopeType.WAREHOUSE, 9999L, organizationId));
    assertTrue(authorizationService.hasPermission(userId, stockAdjust.getCode(), ScopeType.SITE, 1L, organizationId));
  }

  // Phase 5b-1 : voir docs/phase-5b1-report.md. C'est le test qui prouve directement la
  // correction du contournement RBAC cross-tenant : une affectation GLOBAL n'accorde plus la
  // permission "partout dans l'application", seulement "partout dans SA PROPRE organisation".
  @Test
  public void globalAssignmentInOneOrganizationDoesNotGrantAccessInAnother() {
    PermissionDto stockAdjust = permissionService.save(
        PermissionDto.builder().code(uniqueCode("STOCK_ADJUST")).description("Ajuster le stock").build());

    RoleDto directeurGeneral = roleService.save(
        RoleDto.builder().code(uniqueCode("DIRECTEUR_GENERAL")).name("Directeur General")
            .permissions(Set.of(stockAdjust)).build());

    Long userId = 3003L;
    Long ownOrganizationId = createOrganization("Societe Propre");
    Long otherOrganizationId = createOrganization("Societe Autre");

    userRoleAssignmentService.save(
        UserRoleAssignmentDto.builder().userId(userId).role(directeurGeneral)
            .scopeType(ScopeType.GLOBAL).organizationId(ownOrganizationId).build());

    assertTrue(authorizationService.hasGlobalAccess(userId, ownOrganizationId));
    assertTrue(authorizationService.hasPermission(userId, stockAdjust.getCode(), ScopeType.SITE, 1L, ownOrganizationId));

    assertFalse(authorizationService.hasGlobalAccess(userId, otherOrganizationId));
    assertFalse(authorizationService.hasPermission(userId, stockAdjust.getCode(), ScopeType.SITE, 1L, otherOrganizationId));
  }
}
