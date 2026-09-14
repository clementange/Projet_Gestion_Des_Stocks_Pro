package com.kfokam48.gestiondestock.organization.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.organization.application.CityService;
import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
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
  public CityDto save(@RequestBody CityDto dto) {
    return cityService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/cities/{idCity}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CityDto findById(@PathVariable("idCity") Long id) {
    return cityService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/cities/filter/organization/{idOrganization}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CityDto> findAllByOrganization(@PathVariable("idOrganization") Long idOrganization) {
    return cityService.findAllByOrganization(idOrganization);
  }

  @DeleteMapping(value = APP_ROOT + "/cities/delete/{idCity}")
  public void delete(@PathVariable("idCity") Long id) {
    cityService.delete(id);
  }
}
