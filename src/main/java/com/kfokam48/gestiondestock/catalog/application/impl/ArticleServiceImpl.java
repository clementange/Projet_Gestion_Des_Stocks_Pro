package com.kfokam48.gestiondestock.catalog.application.impl;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.catalog.application.validator.ArticleValidator;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.ArticleRepository;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.model.LigneCommandeClient;
import com.kfokam48.gestiondestock.model.LigneCommandeFournisseur;
import com.kfokam48.gestiondestock.model.LigneVente;
import com.kfokam48.gestiondestock.repository.LigneCommandeClientRepository;
import com.kfokam48.gestiondestock.repository.LigneCommandeFournisseurRepository;
import com.kfokam48.gestiondestock.repository.LigneVenteRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

// Phase 23 : le garde-fou de suppression lisait l'usage d'un article uniquement depuis les tables
// legacy lignevente/lignecommandeclient/lignecommandefournisseur. Depuis les Phases 19-21, les
// nouvelles ventes/commandes sont ecrites dans sale_line/customer_order_line/purchase_order_line
// (modules neufs) : pour ne pas laisser supprimer un article encore reellement utilise, l'usage
// cote modules neufs est desormais detecte via la contrainte FK de la base (voir delete()
// ci-dessous), plutot que par un appel direct aux facades sales/purchasing.
//
// Phase 3a : ArticleServiceImpl ne depend plus des modules sales/purchasing du tout. Les methodes
// findHistoriqueVentes/findHistoriqueCommandeClient/findHistoriqueCommandeFournisseur (qui
// fusionnaient legacy + modules neufs pour restituer l'historique d'un article) ont ete deplacees
// vers sales.presentation.rest.legacy.ArticleHistoryLegacyController et
// purchasing.presentation.rest.legacy.ArticleHistoryLegacyController (URL HTTP inchangees). Le
// garde-fou de suppression, lui, ne pouvait pas se contenter d'un deplacement : catalog doit
// pouvoir refuser la suppression d'un article utilise, mais sales/purchasing dependent deja
// legitimement de catalog (ArticleDto) — tout appel de catalog vers sales/purchasing, ou que se
// trouve ce code, aurait recree un cycle catalog <-> sales/purchasing (voir docs/phase-3a-report.md).
// La contrainte FK (article_id) deja presente sur sale_line/customer_order_line/purchase_order_line
// (V1__initial_schema.sql) protege deja la suppression au niveau base ; on se contente de traduire
// l'exception SQL en InvalidOperationException au lieu de pre-verifier via un appel cross-module.
@Service
@Slf4j
public class ArticleServiceImpl implements ArticleService {

  private ArticleRepository articleRepository;
  private LigneVenteRepository venteRepository;
  private LigneCommandeFournisseurRepository commandeFournisseurRepository;
  private LigneCommandeClientRepository commandeClientRepository;

  @Autowired
  public ArticleServiceImpl(
      ArticleRepository articleRepository,
      LigneVenteRepository venteRepository, LigneCommandeFournisseurRepository commandeFournisseurRepository,
      LigneCommandeClientRepository commandeClientRepository) {
    this.articleRepository = articleRepository;
    this.venteRepository = venteRepository;
    this.commandeFournisseurRepository = commandeFournisseurRepository;
    this.commandeClientRepository = commandeClientRepository;
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
    if (!ligneCommandeClients.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des commandes client", ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    List<LigneCommandeFournisseur> ligneCommandeFournisseurs = commandeFournisseurRepository.findAllByArticleId(id);
    if (!ligneCommandeFournisseurs.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des commandes fournisseur",
          ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    List<LigneVente> ligneVentes = venteRepository.findAllByArticleId(id);
    if (!ligneVentes.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer un article deja utilise dans des ventes",
          ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
    try {
      articleRepository.deleteById(id);
    } catch (DataIntegrityViolationException ex) {
      throw new InvalidOperationException(
          "Impossible de supprimer un article deja utilise (vente, commande ou mouvement de stock associe)",
          ErrorCodes.ARTICLE_ALREADY_IN_USE);
    }
  }
}
