package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.VentesDto;
import java.util.List;

public interface VentesService {

  VentesDto save(VentesDto dto);

  VentesDto findById(Long id);

  VentesDto findByCode(String code);

  List<VentesDto> findAll();

  void delete(Long id);

}
