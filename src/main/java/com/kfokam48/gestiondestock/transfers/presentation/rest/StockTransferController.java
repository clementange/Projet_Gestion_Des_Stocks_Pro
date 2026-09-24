package com.kfokam48.gestiondestock.transfers.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.transfers.application.StockTransferService;
import com.kfokam48.gestiondestock.transfers.application.dto.CreateStockTransferRequest;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferDto;
import com.kfokam48.gestiondestock.transfers.application.dto.StockTransferLineDto;
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

@Tag(name = "stock-transfers")
@RestController
public class StockTransferController {

  private StockTransferService stockTransferService;

  @Autowired
  public StockTransferController(StockTransferService stockTransferService) {
    this.stockTransferService = stockTransferService;
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto create(@RequestBody CreateStockTransferRequest request, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.create(request.getTransfer(), request.getLines(), principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto findById(@PathVariable("idTransfert") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/stock-transfers/filter/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto findByCode(@PathVariable("code") String code, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.findByCode(code, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/stock-transfers/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockTransferDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.findAll(principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/lignes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockTransferLineDto> findLines(@PathVariable("idTransfert") Long id) {
    return stockTransferService.findLines(id);
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/demander", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto submit(@PathVariable("idTransfert") Long id) {
    return stockTransferService.submit(id);
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/approuver", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto approve(@PathVariable("idTransfert") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.approve(id, principal.getIdUtilisateur());
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/preparer", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto startPreparation(@PathVariable("idTransfert") Long id) {
    return stockTransferService.startPreparation(id);
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/expedier", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto ship(@PathVariable("idTransfert") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.ship(id, principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/receptionner", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto receive(@PathVariable("idTransfert") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return stockTransferService.receive(id, principal.getIdUtilisateur(), principal.getOrganizationId());
  }

  @PostMapping(value = APP_ROOT + "/stock-transfers/{idTransfert}/annuler", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockTransferDto cancel(@PathVariable("idTransfert") Long id) {
    return stockTransferService.cancel(id);
  }
}
