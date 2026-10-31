package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.kfokam48.gestiondestock.shared.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "cities")
@RestController
public class CityController {

  private CityService cityService;

  @Autowired
  public CityController(CityService cityService) {
    this.cityService = cityService;
  }

  @PostMapping(value = APP_ROOT + "/cities/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CityDto save(@RequestBody CityDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    // Phase 5b-2g : l'organisation ne vient plus du client. Fournie librement, elle permettait de
    // rattacher une ville a l'organisation d'un autre tenant. TOUJOURS ecrasee - voir
    // docs/phase-5b2g-report.md.
    dto.setOrganization(OrganizationDto.builder().id(principal.getOrganizationId()).build());
    return cityService.save(dto, principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/cities/{idCity}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CityDto findById(@PathVariable("idCity") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return cityService.findById(id, principal.getOrganizationId());
  }

  /**
   * Phase 5b-2h : l'organisation du CHEMIN est ignoree - seule celle du principal fait foi.
   * Auparavant, fournir l'id d'une autre organisation retournait ses villes - voir
   * docs/phase-5b2h-report.md.
   */
  @GetMapping(value = APP_ROOT + "/cities/filter/organization/{idOrganization}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CityDto> findAllByOrganization(@PathVariable("idOrganization") Long idOrganization,
      @AuthenticationPrincipal ExtendedUser principal) {
    return cityService.findAllByOrganization(principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/cities/delete/{idCity}")
  public void delete(@PathVariable("idCity") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    cityService.delete(id, principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  /**
   * Phase 5b-2g : seul chemin de mise a jour. L'id vient du CHEMIN ; le service verifie
   * l'appartenance a l'organisation de l'appelant avant toute ecriture et interdit tout
   * changement d'organisation - voir docs/phase-5b2g-report.md.
   */
  @PutMapping(value = APP_ROOT + "/cities/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CityDto update(@PathVariable("id") Long id, @RequestBody CityDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    return cityService.update(id, dto, principal.getIdUtilisateur(), principal.getOrganizationId());
  }
}
