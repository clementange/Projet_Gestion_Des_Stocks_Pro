package com.kfokam48.gestiondestock.catalog.application.impl;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.validator.ArticleValidator;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.ArticleRepository;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneVenteDto;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.LigneCommandeClient;
import com.kfokam48.gestiondestock.model.LigneCommandeFournisseur;
import com.kfokam48.gestiondestock.model.LigneVente;
import com.kfokam48.gestiondestock.purchasing.domain.model.PurchaseOrderLine;
import com.kfokam48.gestiondestock.purchasing.infrastructure.persistence.PurchaseOrderLineRepository;
import com.kfokam48.gestiondestock.repository.LigneCommandeClientRepository;
import com.kfokam48.gestiondestock.repository.LigneCommandeFournisseurRepository;
import com.kfokam48.gestiondestock.repository.LigneVenteRepository;
import com.kfokam48.gestiondestock.sales.domain.model.CustomerOrderLine;
import com.kfokam48.gestiondestock.sales.domain.model.SaleLine;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.CustomerOrderLineRepository;
import com.kfokam48.gestiondestock.sales.infrastructure.persistence.SaleLineRepository;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 23 : Article (Phase 5) lisait l'historique et le garde-fou de suppression uniquement
// depuis les tables legacy lignevente/lignecommandeclient/lignecommandefournisseur. Depuis les
// Phases 19-21, les nouvelles ventes/commandes sont ecrites dans sale_line/customer_order_line/
// purchase_order_line (modules neufs) : les deux sources sont desormais fusionnees pour ne pas
// perdre l'historique post-migration ni laisser supprimer un article encore reellement utilise.
@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {

  private ArticleRepository articleRepository;
  private LigneVenteRepository venteRepository;
  private LigneCommandeFournisseurRepository commandeFournisseurRepository;
  private LigneCommandeClientRepository commandeClientRepository;
  private SaleLineRepository saleLineRepository;
  private CustomerOrderLineRepository customerOrderLineRepository;
  private PurchaseOrderLineRepository purchaseOrderLineRepository;

  @Autowired
  public ArticleServiceImpl(
      ArticleRepository articleRepository,
      LigneVenteRepository venteRepository, LigneCommandeFournisseurRepository commandeFournisseurRepository,
      LigneCommandeClientRepository commandeClientRepository, SaleLineRepository saleLineRepository,
      CustomerOrderLineRepository customerOrderLineRepository, PurchaseOrderLineRepository purchaseOrderLineRepository) {
    this.articleRepository = articleRepository;
    this.venteRepository = venteRepository;
    this.commandeFournisseurRepository = commandeFournisseurRepository;
    this.commandeClientRepository = commandeClientRepository;
    this.saleLineRepository = saleLineRepository;
    this.customerOrderLineRepository = customerOrderLineRepository;
    this.purchaseOrderLineRepository = purchaseOrderLineRepository;
  }

  @Override
  public ArticleDto save(ArticleDto dto) {
    List<String> errors = ArticleValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Article is not valid {}", dto);
      throw new InvalidEntityException("L'article n'est pas valide", ErrorCodes.ARTICLE_NOT_VALID, errors);
    }

    return ArticleDto.fromEntity(
        articleRepository.save(
            ArticleDto.toEntity(dto)
        )
    );
  }

  @Override
  public ArticleDto findById(Long id) {
    if (id == null) {
      log.error("Article ID is null");
      return null;
    }

    return articleRepository.findById(id).map(ArticleDto::fromEntity).orElseThrow(() ->
        new EntityNotFoundException(
            "Aucun article avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.ARTICLE_NOT_FOUND)
    );
  }

  @Override
  public ArticleDto findByCodeArticle(String codeArticle) {
    if (!StringUtils.hasLength(codeArticle)) {
      log.error("Article CODE is null");
      return null;
    }

    return articleRepository.findArticleByCodeArticle(codeArticle)
        .map(ArticleDto::fromEntity)
        .orElseThrow(() ->
            new EntityNotFoundException(
                "Aucun article avec le CODE = " + codeArticle + " n' ete trouve dans la BDD",
                ErrorCodes.ARTICLE_NOT_FOUND)
        );
  }

  @Override
  public List<ArticleDto> findAll() {
    return articleRepository.findAll().stream()
        .map(ArticleDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<LigneVenteDto> findHistoriqueVentes(Long idArticle) {
    Stream<LigneVenteDto> legacy = venteRepository.findAllByArticleId(idArticle).stream()
        .map(LigneVenteDto::fromEntity);
    Stream<LigneVenteDto> fromSales = saleLineRepository.findAllByArticleId(idArticle).stream()
        .map(this::toLigneVenteDto);
    return Stream.concat(legacy, fromSales).collect(Collectors.toList());
  }

  @Override
  public List<LigneCommandeClientDto> findHistoriaueCommandeClient(Long idArticle) {
    Stream<LigneCommandeClientDto> legacy = commandeClientRepository.findAllByArticleId(idArticle).stream()
        .map(LigneCommandeClientDto::fromEntity);
    Stream<LigneCommandeClientDto> fromCustomerOrders = customerOrderLineRepository.findAllByArticleId(idArticle).stream()
        .map(this::toLigneCommandeClientDto);
    return Stream.concat(legacy, fromCustomerOrders).collect(Collectors.toList());
  }

  @Override
  public List<LigneCommandeFournisseurDto> findHistoriqueCommandeFournisseur(Long idArticle) {
    Stream<LigneCommandeFournisseurDto> legacy = commandeFournisseurRepository.findAllByArticleId(idArticle).stream()
        .map(LigneCommandeFournisseurDto::fromEntity);
    Stream<LigneCommandeFournisseurDto> fromPurchaseOrders = purchaseOrderLineRepository.findAllByArticleId(idArticle).stream()
        .map(this::toLigneCommandeFournisseurDto);
    return Stream.concat(legacy, fromPurchaseOrders).collect(Collectors.toList());
  }

  private LigneVenteDto toLigneVenteDto(SaleLine line) {
    return LigneVenteDto.builder()
        .id(line.getId())
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  private LigneCommandeClientDto toLigneCommandeClientDto(CustomerOrderLine line) {
    return LigneCommandeClientDto.builder()
        .id(line.getId())
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantite())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  private LigneCommandeFournisseurDto toLigneCommandeFournisseurDto(PurchaseOrderLine line) {
    return LigneCommandeFournisseurDto.builder()
        .id(line.getId())
        .article(ArticleDto.fromEntity(line.getArticle()))
        .quantite(line.getQuantiteCommandee())
        .prixUnitaire(line.getPrixUnitaire())
        .build();
  }

  @Override
  public List<ArticleDto> findAllArticleByIdCategory(Long idCategory) {
    return articleRepository.findAllByCategoryId(idCategory).stream()
        .map(ArticleDto::fromEntity)
        .collect(Collectors.toList());
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Article ID is null");
      return;
    }
    List<LigneCommandeClient> ligneCommandeClients = commandeClientRepository.findAllByArticleId(id);
    List<CustomerOrderLine> customerOrderLines = customerOrderLineRepository.findAllByArticleId(id);
    if (!ligneCommandeClients.isEmpty() || !customerOrderLines.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des commandes client", ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    List<LigneCommandeFournisseur> ligneCommandeFournisseurs = commandeFournisseurRepository.findAllByArticleId(id);
    List<PurchaseOrderLine> purchaseOrderLines = purchaseOrderLineRepository.findAllByArticleId(id);
    if (!ligneCommandeFournisseurs.isEmpty() || !purchaseOrderLines.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des commandes fournisseur",
          ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    List<LigneVente> ligneVentes = venteRepository.findAllByArticleId(id);
    List<SaleLine> saleLines = saleLineRepository.findAllByArticleId(id);
    if (!ligneVentes.isEmpty() || !saleLines.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des ventes",
          ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    articleRepository.deleteById(id);
  }
}
