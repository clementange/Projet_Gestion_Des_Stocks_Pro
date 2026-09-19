package com.kfokam48.gestiondestock.sales.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.CreateSaleRequest;
import com.kfokam48.gestiondestock.sales.application.dto.SaleDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "sales")
@RestController
public class SaleController {

  private SaleService saleService;

  @Autowired
  public SaleController(SaleService saleService) {
    this.saleService = saleService;
  }

  @PostMapping(value = APP_ROOT + "/sales/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public SaleDto create(@RequestBody CreateSaleRequest request, @AuthenticationPrincipal ExtendedUser principal) {
    return saleService.create(request.getSale(), request.getLines(), principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/sales/{idVente}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SaleDto findById(@PathVariable("idVente") Long id) {
    return saleService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/sales/filter/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  public SaleDto findByCode(@PathVariable("code") String code) {
    return saleService.findByCode(code);
  }

  @GetMapping(value = APP_ROOT + "/sales/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SaleDto> findAll() {
    return saleService.findAll();
  }

  @GetMapping(value = APP_ROOT + "/sales/{idVente}/lignes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<SaleLineDto> findLines(@PathVariable("idVente") Long id) {
    return saleService.findLines(id);
  }
}
