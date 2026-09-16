package com.kfokam48.gestiondestock.tenant.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.tenant.application.TenantRegistrationService;
import com.kfokam48.gestiondestock.tenant.application.TenantService;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantDto;
import com.kfokam48.gestiondestock.tenant.application.dto.TenantRegistrationRequest;
import com.kfokam48.gestiondestock.media.application.dto.PhotoUrlRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4b : contrat canonique pour l'inscription d'une entreprise (successeur de
 * {@code /entreprises/create}, supprime - voir docs/phase-4b-report.md). {@code /register} est
 * public (SecurityConfiguration), comme {@code /entreprises/create} l'etait. Les endpoints de
 * lecture/suppression ont la meme posture que {@code /users/*} en Phase 4a : authenticated() seul,
 * aucune verification de permission fine - pas une amelioration deliberee.
 */
@Tag(name = "tenants")
@RestController
@Slf4j
public class TenantController {

  private TenantRegistrationService tenantRegistrationService;
  private TenantService tenantService;

  @Autowired
  public TenantController(TenantRegistrationService tenantRegistrationService, TenantService tenantService) {
    this.tenantRegistrationService = tenantRegistrationService;
    this.tenantService = tenantService;
  }

  @PostMapping(value = APP_ROOT + "/tenants/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public TenantDto register(@RequestBody TenantRegistrationRequest request) {
    return tenantRegistrationService.register(request);
  }

  @GetMapping(value = APP_ROOT + "/tenants/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public TenantDto findById(@PathVariable("id") Long id) {
    return tenantService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/tenants/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<TenantDto> findAll() {
    return tenantService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/tenants/delete/{id}")
  public void delete(@PathVariable("id") Long id) {
    tenantService.delete(id);
  }

  // Phase 4c : remplace /save/{id}/{title}/entreprise (StrategyPhotoContext, supprime).
  @PostMapping(value = APP_ROOT + "/tenants/{id}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public TenantDto updatePhoto(@PathVariable("id") Long id, @RequestBody PhotoUrlRequest request) {
    return tenantService.updatePhoto(id, request.url());
  }
}
