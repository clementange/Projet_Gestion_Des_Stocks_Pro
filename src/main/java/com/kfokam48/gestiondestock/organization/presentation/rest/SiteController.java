package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.SiteService;
import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 5b-2b : la creation (save) ne verifie pas que la City referencee appartient a
 * l'organisation de l'appelant - chaine City/Organization plus profonde que les 6 entites
 * couvertes par cet increment (Sale/CustomerOrder/PurchaseOrder/StockTransfer/Site/Stock),
 * deliberement laissee ouverte - voir docs/phase-5b2b-report.md.
 */
@Tag(name = "sites")
@RestController
public class SiteController {

  private SiteService siteService;

  @Autowired
  public SiteController(SiteService siteService) {
    this.siteService = siteService;
  }

  @PostMapping(value = APP_ROOT + "/sites/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public SiteDto save(@RequestBody SiteDto dto) {
    return siteService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/sites/{idSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SiteDto findById(@PathVariable("idSite") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return siteService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/sites/filter/{codeSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SiteDto findByCode(@PathVariable("codeSite") String code, @AuthenticationPrincipal ExtendedUser principal) {
    return siteService.findByCode(code, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/sites/filter/city/{idCity}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SiteDto> findAllByCity(@PathVariable("idCity") Long idCity, @AuthenticationPrincipal ExtendedUser principal) {
    return siteService.findAllByCity(idCity, principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/sites/delete/{idSite}")
  public void delete(@PathVariable("idSite") Long id) {
    siteService.delete(id);
  }
}
