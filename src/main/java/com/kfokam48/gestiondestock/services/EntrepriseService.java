package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.EntrepriseDto;
import java.util.List;

public interface EntrepriseService {

  EntrepriseDto save(EntrepriseDto dto);

  EntrepriseDto findById(Long id);

  List<EntrepriseDto> findAll();

  void delete(Long id);

}
