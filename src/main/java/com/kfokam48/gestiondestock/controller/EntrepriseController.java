package com.kfokam48.gestiondestock.controller;

import com.kfokam48.gestiondestock.controller.api.EntrepriseApi;
import com.kfokam48.gestiondestock.dto.EntrepriseDto;
import com.kfokam48.gestiondestock.services.EntrepriseService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntrepriseController implements EntrepriseApi {

  private EntrepriseService entrepriseService;

  @Autowired
  public EntrepriseController(EntrepriseService entrepriseService) {
    this.entrepriseService = entrepriseService;
  }

  @Override
  public EntrepriseDto save(EntrepriseDto dto) {
    return entrepriseService.save(dto);
  }

  @Override
  public EntrepriseDto findById(Long id) {
    return entrepriseService.findById(id);
  }

  @Override
  public List<EntrepriseDto> findAll() {
    return entrepriseService.findAll();
  }

  @Override
  public void delete(Long id) {
    entrepriseService.delete(id);
  }
}
