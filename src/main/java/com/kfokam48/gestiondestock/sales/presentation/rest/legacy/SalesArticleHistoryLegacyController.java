package com.kfokam48.gestiondestock.sales.presentation.rest.legacy;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneVenteDto;
import com.kfokam48.gestiondestock.repository.LigneCommandeClientRepository;
import com.kfokam48.gestiondestock.repository.LigneVenteRepository;
import com.kfokam48.gestiondestock.sales.application.CustomerOrderService;
import com.kfokam48.gestiondestock.sales.application.SaleService;
import com.kfokam48.gestiondestock.sales.application.dto.CustomerOrderLineDto;
import com.kfokam48.gestiondestock.sales.application.dto.SaleLineDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 3a : sert le contrat HTTP legacy {@code /articles/historique/vente|commandeclient/{id}}
 * (auparavant expose depuis catalog.ArticleController). Deplace ici, dans le module sales, pour
 * que le calcul reste au plus pres des donnees qu'il combine (SaleLine/CustomerOrderLine + tables
 * legacy lignevente/lignecommandeclient) sans que catalog n'ait besoin de dependre du module sales
 * — dependance qui aurait forme un cycle catalog {@literal <->} sales (sales depend legitimement
 * de catalog.ArticleDto, voir docs/migration-notes.md "Trouve pendant Phase 1"). L'URL HTTP est
 * inchangee : seul l'emplacement du code source change, aucun impact contrat pour les
 * consommateurs existants.
 */
@Tag(name = "articles")
@RestController
public class SalesArticleHistoryLegacyController {

  private final LigneVenteRepository venteRepository;
  private final LigneCommandeClientRepository commandeClientRepository;
  private final SaleService saleService;
  private final CustomerOrderService customerOrderService;

  @Autowired
  public SalesArticleHistoryLegacyController(LigneVenteRepository venteRepository,
      LigneCommandeClientRepository commandeClientRepository, SaleService saleService,
      CustomerOrderService customerOrderService) {
    this.venteRepository = venteRepository;
    this.commandeClientRepository = commandeClientRepository;
    this.saleService = saleService;
    this.customerOrderService = customerOrderService;
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/vente/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneVenteDto> findHistoriqueVentes(@PathVariable("idArticle") Long idArticle) {
    Stream<LigneVenteDto> legacy = venteRepository.findAllByArticleId(idArticle).stream()
        .map(LigneVenteDto::fromEntity);
    Stream<LigneVenteDto> fromSales = saleService.findLinesByArticleId(idArticle).stream()
        .map(this::toLigneVenteDto);
    return Stream.concat(legacy, fromSales).collect(Collectors.toList());
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/commandeclient/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneCommandeClientDto> findHistoriqueCommandeClient(@PathVariable("idArticle") Long idArticle) {
    Stream<LigneCommandeClientDto> legacy = commandeClientRepository.findAllByArticleId(idArticle).stream()
        .map(LigneCommandeClientDto::fromEntity);
    Stream<LigneCommandeClientDto> fromCustomerOrders = customerOrderService.findLinesByArticleId(idArticle).stream()
        .map(this::toLigneCommandeClientDto);
    return Stream.concat(legacy, fromCustomerOrders).collect(Collectors.toList());
  }

  private LigneVenteDto toLigneVenteDto(SaleLineDto line) {
    return LigneVenteDto.builder()
        .id(line.getId())
        .article(line.getArticle())
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  private LigneCommandeClientDto toLigneCommandeClientDto(CustomerOrderLineDto line) {
    return LigneCommandeClientDto.builder()
        .id(line.getId())
        .article(line.getArticle())
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }
}
