package com.kfokam48.gestiondestock.purchasing.presentation.rest.legacy;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.purchasing.application.PurchaseOrderService;
import com.kfokam48.gestiondestock.purchasing.application.dto.PurchaseOrderLineDto;
import com.kfokam48.gestiondestock.repository.LigneCommandeFournisseurRepository;
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
 * Phase 3a : sert le contrat HTTP legacy
 * {@code /articles/historique/commandefournisseur/{id}} (auparavant expose depuis
 * catalog.ArticleController). Voir le commentaire de
 * {@code sales.presentation.rest.legacy.SalesArticleHistoryLegacyController} pour la justification
 * complete de ce deplacement (evite un cycle catalog {@literal <->} purchasing). URL HTTP
 * inchangee.
 */
@Tag(name = "articles")
@RestController
public class PurchaseOrderArticleHistoryLegacyController {

  private final LigneCommandeFournisseurRepository commandeFournisseurRepository;
  private final PurchaseOrderService purchaseOrderService;

  @Autowired
  public PurchaseOrderArticleHistoryLegacyController(LigneCommandeFournisseurRepository commandeFournisseurRepository,
      PurchaseOrderService purchaseOrderService) {
    this.commandeFournisseurRepository = commandeFournisseurRepository;
    this.purchaseOrderService = purchaseOrderService;
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/commandefournisseur/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneCommandeFournisseurDto> findHistoriqueCommandeFournisseur(@PathVariable("idArticle") Long idArticle) {
    Stream<LigneCommandeFournisseurDto> legacy = commandeFournisseurRepository.findAllByArticleId(idArticle).stream()
        .map(LigneCommandeFournisseurDto::fromEntity);
    Stream<LigneCommandeFournisseurDto> fromPurchaseOrders = purchaseOrderService.findLinesByArticleId(idArticle).stream()
        .map(this::toLigneCommandeFournisseurDto);
    return Stream.concat(legacy, fromPurchaseOrders).collect(Collectors.toList());
  }

  private LigneCommandeFournisseurDto toLigneCommandeFournisseurDto(PurchaseOrderLineDto line) {
    return LigneCommandeFournisseurDto.builder()
        .id(line.getId())
        .article(line.getArticle())
        .quantite(line.getQuantiteCommandee())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }
}
