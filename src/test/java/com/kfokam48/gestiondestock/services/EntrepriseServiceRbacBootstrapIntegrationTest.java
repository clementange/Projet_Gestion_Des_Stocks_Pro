package com.kfokam48.gestiondestock.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.identity.application.UserRoleAssignmentService;
import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.tenant.application.TenantRegistrationService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 14 : TenantRegistrationService.register() doit amorcer le RBAC du premier utilisateur
 * d'une organisation (role ADMINISTRATEUR, scope GLOBAL), sinon personne ne pourrait jamais
 * attribuer de role via user-role-assignments/create (aucun utilisateur n'aurait de permission au
 * depart). Phase 4b : reecrit (pas simplement adapte) - meme raison que
 * EntrepriseServiceOrganizationMirrorIntegrationTest. Recherche de l'utilisateur admin desormais
 * via identity.application.UserService (le module qui le possede reellement), pas via
 * l'UtilisateurRepository legacy orphelin.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class EntrepriseServiceRbacBootstrapIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TenantRegistrationService tenantRegistrationService;

  @Autowired
  private UserService userService;

  @Autowired
  private UserRoleAssignmentService userRoleAssignmentService;

  private static String uniqueEmail() {
    return "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
  }

  @Test
  public void creatingAnEntrepriseGrantsItsAdminUserAGlobalAdministrateurRole() {
    String email = uniqueEmail();
    tenantRegistrationService.register(new TenantRegistrationRequest(
        "Societe Bootstrap RBAC", "Test bootstrap RBAC",
        AdresseDto.builder().adresse1("1 Rue Test").ville("Douala").pays("Cameroun").codePostale("00000").build(),
        "CF-" + UUID.randomUUID().toString().substring(0, 8), null, email, "+237600000000", null,
        "Admin", "RBAC", email, Instant.parse("1990-01-01T00:00:00Z"),
        "Test-Passw0rd!", "Test-Passw0rd!"));

    Long adminUserId = userService.findByEmail(email).getId();

    List<UserRoleAssignmentDto> assignments = userRoleAssignmentService.findAllByUser(adminUserId);
    assertEquals(1, assignments.size());
    assertEquals(ScopeType.GLOBAL, assignments.get(0).getScopeType());
    assertEquals("ADMINISTRATEUR", assignments.get(0).getRole().getCode());
    assertTrue(assignments.get(0).getRole().getPermissions().stream().anyMatch(p -> "SALE_CREATE".equals(p.getCode())));
  }
}
