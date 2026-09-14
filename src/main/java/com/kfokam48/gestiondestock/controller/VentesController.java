package com.kfokam48.gestiondestock.controller;


import com.kfokam48.gestiondestock.controller.api.VentesApi;
import com.kfokam48.gestiondestock.dto.VentesDto;
import com.kfokam48.gestiondestock.services.VentesService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VentesController implements VentesApi {

  private VentesService ventesService;

  @Autowired
  public VentesController(VentesService ventesService) {
    this.ventesService = ventesService;
  }

  @Override
  public VentesDto save(VentesDto dto) {
    return ventesService.save(dto);
  }

  @Override
  public VentesDto findById(Long id) {
    return ventesService.findById(id);
  }

  @Override
  public VentesDto findByCode(String code) {
    return ventesService.findByCode(code);
  }

  @Override
  public List<VentesDto> findAll() {
    return ventesService.findAll();
  }

  @Override
  public void delete(Long id) {
    ventesService.delete(id);
  }
}
