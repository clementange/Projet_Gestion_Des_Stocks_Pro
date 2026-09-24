package com.kfokam48.gestiondestock.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.kfokam48.gestiondestock.support.AbstractIntegrationTest;

/**
 * Phase 5b-2c : findById/findAll/delete/updatePhoto sont desormais self-only - voir
 * docs/phase-5b2c-report.md. Le flow d'inscription complet (Tenant + Organization miroir +
 * admin + RBAC) est deja teste par TenantRegistrationCharacterizationTest ; ce fichier couvre
 * uniquement le CRUD plat de TenantService (save() est un upsert sans orchestration).
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class TenantServiceImplTest extends AbstractIntegrationTest {

  @Autowired
  private TenantService tenantService;

  private static String uniqueCode(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private TenantDto createTenant(String nom) {
    return tenantService.save(TenantDto.builder()
        .nom(nom)
        .description("x")
        .codeFiscal(uniqueCode("CF"))
        .email(uniqueCode("tenant") + "@test.local")
        .numTel("+237600000000")
        .adresse(AdresseDto.builder().adresse1("1 Rue").ville("Douala").pays("Cameroun").codePostale("00000").build())
        .build());
  }

  @Test
  public void tenantAccessIsSelfOnly() {
    TenantDto tenantA = createTenant("Tenant Self A");
    TenantDto tenantB = createTenant("Tenant Self B");

    assertEquals(tenantA.getId(), tenantService.findById(tenantA.getId(), tenantA.getId()).getId());
    assertEquals(1, tenantService.findAll(tenantA.getId()).size());

    assertThrows(EntityNotFoundException.class, () -> tenantService.findById(tenantA.getId(), tenantB.getId()));
    // findAll(tenantB) voit son propre tenant (1 element), jamais tenantA.
    assertEquals(1, tenantService.findAll(tenantB.getId()).size());
    assertEquals(tenantB.getId(), tenantService.findAll(tenantB.getId()).get(0).getId());
  }

  @Test
  public void updatePhotoIsSelfOnly() {
    TenantDto tenantA = createTenant("Tenant Photo A");
    TenantDto tenantB = createTenant("Tenant Photo B");

    assertThrows(EntityNotFoundException.class,
        () -> tenantService.updatePhoto(tenantA.getId(), "https://example.test/photo.png", tenantB.getId()));

    TenantDto updated = tenantService.updatePhoto(tenantA.getId(), "https://example.test/photo.png", tenantA.getId());
    assertEquals("https://example.test/photo.png", updated.getPhoto());
  }

  @Test
  public void deleteIsSelfOnly() {
    TenantDto tenantA = createTenant("Tenant Delete A");
    TenantDto tenantB = createTenant("Tenant Delete B");

    assertThrows(EntityNotFoundException.class, () -> tenantService.delete(tenantA.getId(), tenantB.getId()));
    // tenantA existe toujours.
    assertEquals(tenantA.getId(), tenantService.findById(tenantA.getId(), tenantA.getId()).getId());
  }

  // findById(Long) sans organisation reste utilise en interne (ApplicationUserDetailsService au
  // login, adaptateur legacy UtilisateurServiceImpl) : pas de filtrage.
  @Test
  public void internalUnfilteredFindByIdStillWorks() {
    TenantDto tenantA = createTenant("Tenant Internal A");
    assertEquals(tenantA.getId(), tenantService.findById(tenantA.getId()).getId());
  }
}
