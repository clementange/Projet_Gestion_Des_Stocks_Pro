package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.FournisseurDto;
import java.util.List;

public interface FournisseurService {

  FournisseurDto save(FournisseurDto dto);

  FournisseurDto findById(Long id);

  List<FournisseurDto> findAll();

  void delete(Long id);

}
