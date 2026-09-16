package com.kfokam48.gestiondestock.sales.presentation.rest.legacy;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.media.application.dto.PhotoUrlRequest;
import com.kfokam48.gestiondestock.sales.application.CustomerService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4c : sert le contrat HTTP legacy {@code /clients/{id}/photo} (auparavant
 * {@code /save/{id}/{title}/client} via StrategyPhotoContext/SaveClientPhoto, supprimes). Place
 * ici, dans {@code sales.presentation.rest.legacy}, plutot que dans le legacy plat
 * ({@code controller/}, {@code services/}) : CLAUDE.md interdit toute nouvelle fonctionnalite
 * dans le legacy plat, meme motif que {@code SalesArticleHistoryLegacyController} (Phase 3a) -
 * voir docs/phase-4c-report.md. L'URL HTTP reprend celle du contrat legacy Client
 * ({@code /clients/*}), pas une nouvelle route sous {@code /customers/*} qui n'existe pas encore.
 */
@Tag(name = "clients")
@RestController
public class ClientPhotoLegacyController {

  private final CustomerService customerService;

  @Autowired
  public ClientPhotoLegacyController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping(value = APP_ROOT + "/clients/{idClient}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CustomerDto updatePhoto(@PathVariable("idClient") Long id, @RequestBody PhotoUrlRequest request) {
    return customerService.updatePhoto(id, request.url());
  }
}
