package com.kfokam48.gestiondestock.organization.application;

import com.kfokam48.gestiondestock.organization.application.dto.SiteDto;
import java.util.List;

public interface SiteService {

  SiteDto save(SiteDto dto);

  SiteDto findById(Long id);

  SiteDto findByCode(String code);

  List<SiteDto> findAllByCity(Long cityId);

  List<SiteDto> findAll();

  void delete(Long id);

}
