package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.organization.application.OrganizationService;
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

@Tag(name = "organizations")
@RestController
public class OrganizationController {

  private OrganizationService organizationService;

  @Autowired
  public OrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @PostMapping(value = APP_ROOT + "/organizations/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public OrganizationDto save(@RequestBody OrganizationDto dto) {
    return organizationService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/organizations/{idOrganization}", produces = MediaType.APPLICATION_JSON_VALUE)
  public OrganizationDto findById(@PathVariable("idOrganization") Long id) {
    return organizationService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/organizations/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<OrganizationDto> findAll() {
    return organizationService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/organizations/delete/{idOrganization}")
  public void delete(@PathVariable("idOrganization") Long id) {
    organizationService.delete(id);
  }
}
