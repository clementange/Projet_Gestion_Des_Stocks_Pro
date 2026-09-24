package com.kfokam48.gestiondestock.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.identity.application.UserService;
import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 5b-2c : findById/findByEmail/findAll/delete sont scopes au tenant de l'appelant
 * (User.idEntreprise, pas organizationId) - voir docs/phase-5b2c-report.md.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class UserServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private UserService userService;

  @Autowired
  private TenantService tenantService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  // User.idEntreprise porte une contrainte FK reelle vers entreprise(id) malgre l'absence de
  // @ManyToOne cote JPA - il faut un vrai Tenant, pas un id fabrique.
  private Long createTenant(String nom) {
    return tenantService.save(TenantDto.builder()
        .nom(nom)
        .description("x")
        .codeFiscal(uniqueCode("CF"))
        .email(uniqueCode("tenant") + "@test.local")
        .numTel("+237600000000")
        .adresse(AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build()).getId();
  }

  private UserDto createUser(String email, Long idEntreprise) {
    return userService.save(UserDto.builder()
        .nom("Nom")
        .prenom("Prenom")
        .email(email)
        .motDePasse("Passw0rd!")
        .dateDeNaissance(Instant.parse("1990-01-01T00:00:00Z"))
        .adresse(AdresseDto.builder().adresse1("x").ville("x").pays("x").codePostale("00000").build())
        .idEntreprise(idEntreprise)
        .build());
  }

  @Test
  public void userAccessIsScopedToCallerTenant() {
    Long tenantA = createTenant("Tenant Users A");
    Long tenantB = createTenant("Tenant Users B");
    String emailA = uniqueCode("usr-a") + "@test.local";
    UserDto userA = createUser(emailA, tenantA);

    assertEquals(userA.getId(), userService.findById(userA.getId(), tenantA).getId());
    assertEquals(userA.getId(), userService.findByEmail(emailA, tenantA).getId());
    assertEquals(1, userService.findAll(tenantA).size());

    assertThrows(EntityNotFoundException.class, () -> userService.findById(userA.getId(), tenantB));
    assertThrows(EntityNotFoundException.class, () -> userService.findByEmail(emailA, tenantB));
    assertEquals(0, userService.findAll(tenantB).size());
  }

  @Test
  public void deleteIsScopedToCallerTenant() {
    Long tenantA = createTenant("Tenant Users Del A");
    Long tenantB = createTenant("Tenant Users Del B");
    UserDto userA = createUser(uniqueCode("usr-del") + "@test.local", tenantA);

    assertThrows(EntityNotFoundException.class, () -> userService.delete(userA.getId(), tenantB));
    // userA existe toujours.
    assertEquals(userA.getId(), userService.findById(userA.getId(), tenantA).getId());
  }

  // findById(Long)/findByEmail(String)/findAll()/delete(Long) sans tenant restent utilises en
  // interne (ApplicationUserDetailsService au login - findByEmail doit rester global - et
  // l'adaptateur legacy UtilisateurServiceImpl) : pas de filtrage.
  @Test
  public void internalUnfilteredMethodsStillWork() {
    Long tenantA = createTenant("Tenant Users Internal");
    String email = uniqueCode("usr-internal") + "@test.local";
    UserDto userA = createUser(email, tenantA);

    assertEquals(userA.getId(), userService.findById(userA.getId()).getId());
    assertEquals(userA.getId(), userService.findByEmail(email).getId());
  }
}
