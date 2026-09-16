package com.kfokam48.gestiondestock.purchasing.presentation.rest.legacy;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.media.application.dto.PhotoUrlRequest;
import com.kfokam48.gestiondestock.purchasing.application.SupplierService;
import com.kfokam48.gestiondestock.purchasing.application.dto.SupplierDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 4c : sert le contrat HTTP legacy {@code /fournisseurs/{id}/photo} (auparavant
 * {@code /save/{id}/{title}/fournisseur} via StrategyPhotoContext/SaveFournisseurPhoto,
 * supprimes). Meme raisonnement que {@code ClientPhotoLegacyController} - voir
 * docs/phase-4c-report.md.
 */
@Tag(name = "fournisseurs")
@RestController
public class FournisseurPhotoLegacyController {

  private final SupplierService supplierService;

  @Autowired
  public FournisseurPhotoLegacyController(SupplierService supplierService) {
    this.supplierService = supplierService;
  }

  @PostMapping(value = APP_ROOT + "/fournisseurs/{idFournisseur}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public SupplierDto updatePhoto(@PathVariable("idFournisseur") Long id, @RequestBody PhotoUrlRequest request) {
    return supplierService.updatePhoto(id, request.url());
  }
}
