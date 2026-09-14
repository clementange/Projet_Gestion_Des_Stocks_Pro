package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.CityDto;
import java.util.List;

public interface CityService {

  CityDto save(CityDto dto);

  CityDto findById(Long id);

  List<CityDto> findAllByOrganization(Long organizationId);

  void delete(Long id);

}
