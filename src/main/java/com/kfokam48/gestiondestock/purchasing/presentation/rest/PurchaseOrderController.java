package com.kfokam48.gestiondestock.purchasing.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.dto.CreatePurchaseOrderRequest;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderDto;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "purchase-orders")
@RestController
public class PurchaseOrderController {

  private PurchaseOrderService purchaseOrderService;

  @Autowired
  public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
    this.purchaseOrderService = purchaseOrderService;
  }

  @PostMapping(value = APP_ROOT + "/purchase-orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderDto create(@RequestBody CreatePurchaseOrderRequest request) {
    return purchaseOrderService.create(request.getOrder(), request.getLines());
  }

  @GetMapping(value = APP_ROOT + "/purchase-orders/{idCommande}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderDto findById(@PathVariable("idCommande") Long id) {
    return purchaseOrderService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/purchase-orders/filter/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderDto findByCode(@PathVariable("code") String code) {
    return purchaseOrderService.findByCode(code);
  }

  @GetMapping(value = APP_ROOT + "/purchase-orders/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PurchaseOrderDto> findAll() {
    return purchaseOrderService.findAll();
  }

  @GetMapping(value = APP_ROOT + "/purchase-orders/{idCommande}/lignes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PurchaseOrderLineDto> findLines(@PathVariable("idCommande") Long id) {
    return purchaseOrderService.findLines(id);
  }

  @PostMapping(value = APP_ROOT + "/purchase-orders/{idCommande}/valider", produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderDto validate(@PathVariable("idCommande") Long id) {
    return purchaseOrderService.validate(id);
  }

  @PostMapping(value = APP_ROOT + "/purchase-orders/{idCommande}/annuler", produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderDto cancel(@PathVariable("idCommande") Long id) {
    return purchaseOrderService.cancel(id);
  }

  @PostMapping(value = APP_ROOT + "/purchase-orders/{idCommande}/lignes/{idLigne}/receptionner", produces = MediaType.APPLICATION_JSON_VALUE)
  public PurchaseOrderLineDto receiveLine(@PathVariable("idCommande") Long idCommande, @PathVariable("idLigne") Long idLigne,
      @RequestParam BigDecimal quantite, @AuthenticationPrincipal ExtendedUser principal) {
    return purchaseOrderService.receiveLine(idCommande, idLigne, quantite, principal.getIdUtilisateur(), principal.getOrganizationId());
  }
}
