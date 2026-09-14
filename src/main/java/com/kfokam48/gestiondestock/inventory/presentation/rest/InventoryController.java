package com.kfokam48.gestiondestock.inventory.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.inventory.application.InventoryFacade;
import com.kfokam48.gestiondestock.inventory.application.dto.StockDto;
import com.kfokam48.gestiondestock.inventory.application.dto.StockMovementDto;
import com.kfokam48.gestiondestock.inventory.domain.model.StockMovementSource;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "inventory")
@RestController
public class InventoryController {

  private InventoryFacade inventoryFacade;

  @Autowired
  public InventoryController(InventoryFacade inventoryFacade) {
    this.inventoryFacade = inventoryFacade;
  }

  @GetMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockDto getStock(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite) {
    return inventoryFacade.getStock(idArticle, idSite);
  }

  @GetMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/mouvements", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<StockMovementDto> findMovements(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite) {
    return inventoryFacade.findMovements(idArticle, idSite);
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/entree", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto receive(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal quantite, @RequestParam(required = false) String reference) {
    return inventoryFacade.receive(idArticle, idSite, quantite, StockMovementSource.CORRECTION, reference, null);
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/sortie", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto issue(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal quantite, @RequestParam(required = false) String reference) {
    return inventoryFacade.issue(idArticle, idSite, quantite, StockMovementSource.CORRECTION, reference, null);
  }

  @PostMapping(value = APP_ROOT + "/stocks/article/{idArticle}/site/{idSite}/correction", produces = MediaType.APPLICATION_JSON_VALUE)
  public StockMovementDto correct(@PathVariable("idArticle") Long idArticle, @PathVariable("idSite") Long idSite,
      @RequestParam BigDecimal delta, @RequestParam(required = false) String reference) {
    return inventoryFacade.correct(idArticle, idSite, delta, reference, null);
  }
}
