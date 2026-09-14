package com.kfokam48.gestiondestock.controller;

import com.kfokam48.gestiondestock.controller.api.MvtStkApi;
import com.kfokam48.gestiondestock.dto.MvtStkDto;
import com.kfokam48.gestiondestock.services.MvtStkService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MvtStkController implements MvtStkApi {

  private MvtStkService service;

  @Autowired
  public MvtStkController(MvtStkService service) {
    this.service = service;
  }

  @Override
  public BigDecimal stockReelArticle(Long idArticle) {
    return service.stockReelArticle(idArticle);
  }

  @Override
  public List<MvtStkDto> mvtStkArticle(Long idArticle) {
    return service.mvtStkArticle(idArticle);
  }

  @Override
  public MvtStkDto entreeStock(MvtStkDto dto) {
    return service.entreeStock(dto);
  }

  @Override
  public MvtStkDto sortieStock(MvtStkDto dto) {
    return service.sortieStock(dto);
  }

  @Override
  public MvtStkDto correctionStockPos(MvtStkDto dto) {
    return service.correctionStockPos(dto);
  }

  @Override
  public MvtStkDto correctionStockNeg(MvtStkDto dto) {
    return service.correctionStockNeg(dto);
  }
}
