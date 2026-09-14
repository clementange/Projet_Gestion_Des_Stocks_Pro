package com.kfokam48.gestiondestock.sales.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.dto.CreateCustomerOrderRequest;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderDto;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
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

@Tag(name = "customer-orders")
@RestController
public class CustomerOrderController {

  private CustomerOrderService customerOrderService;

  @Autowired
  public CustomerOrderController(CustomerOrderService customerOrderService) {
    this.customerOrderService = customerOrderService;
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto create(@RequestBody CreateCustomerOrderRequest request) {
    return customerOrderService.create(request.getOrder(), request.getLines());
  }

  @GetMapping(value = APP_ROOT + "/customer-orders/{idCommande}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto findById(@PathVariable("idCommande") Long id) {
    return customerOrderService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/customer-orders/filter/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto findByCode(@PathVariable("code") String code) {
    return customerOrderService.findByCode(code);
  }

  @GetMapping(value = APP_ROOT + "/customer-orders/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CustomerOrderDto> findAll() {
    return customerOrderService.findAll();
  }

  @GetMapping(value = APP_ROOT + "/customer-orders/{idCommande}/lignes", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CustomerOrderLineDto> findLines(@PathVariable("idCommande") Long id) {
    return customerOrderService.findLines(id);
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/valider", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto validate(@PathVariable("idCommande") Long id) {
    return customerOrderService.validate(id);
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/reserver", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto reserve(@PathVariable("idCommande") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return customerOrderService.reserve(id, principal.getIdUtilisateur());
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/preparer", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto prepare(@PathVariable("idCommande") Long id) {
    return customerOrderService.prepare(id);
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/expedier", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto ship(@PathVariable("idCommande") Long id) {
    return customerOrderService.ship(id);
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/livrer", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto deliver(@PathVariable("idCommande") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return customerOrderService.deliver(id, principal.getIdUtilisateur());
  }

  @PostMapping(value = APP_ROOT + "/customer-orders/{idCommande}/annuler", produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerOrderDto cancel(@PathVariable("idCommande") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return customerOrderService.cancel(id, principal.getIdUtilisateur());
  }
}
