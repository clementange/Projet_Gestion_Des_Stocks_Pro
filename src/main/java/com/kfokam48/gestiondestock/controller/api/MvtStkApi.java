package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.dto.MvtStkDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "mvtstk")
public interface MvtStkApi {

  @GetMapping(APP_ROOT + "/mvtstk/stockreel/{idArticle}")
  BigDecimal stockReelArticle(@PathVariable("idArticle") Long idArticle);

  @GetMapping(APP_ROOT + "/mvtstk/filter/article/{idArticle}")
  List<MvtStkDto> mvtStkArticle(@PathVariable("idArticle") Long idArticle);

  @PostMapping(APP_ROOT + "/mvtstk/entree")
  MvtStkDto entreeStock(@RequestBody MvtStkDto dto);

  @PostMapping(APP_ROOT + "/mvtstk/sortie")
  MvtStkDto sortieStock(@RequestBody MvtStkDto dto);

  @PostMapping(APP_ROOT + "/mvtstk/correctionpos")
  MvtStkDto correctionStockPos(@RequestBody MvtStkDto dto);

  @PostMapping(APP_ROOT + "/mvtstk/correctionneg")
  MvtStkDto correctionStockNeg(@RequestBody MvtStkDto dto);

}
