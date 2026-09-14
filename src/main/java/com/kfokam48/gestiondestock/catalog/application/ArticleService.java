package com.kfokam48.gestiondestock.catalog.application;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneVenteDto;
import java.util.List;

public interface ArticleService {

  ArticleDto save(ArticleDto dto);

  ArticleDto findById(Long id);

  ArticleDto findByCodeArticle(String codeArticle);

  List<ArticleDto> findAll();

  List<LigneVenteDto> findHistoriqueVentes(Long idArticle);

  List<LigneCommandeClientDto> findHistoriaueCommandeClient(Long idArticle);

  List<LigneCommandeFournisseurDto> findHistoriqueCommandeFournisseur(Long idArticle);

  List<ArticleDto> findAllArticleByIdCategory(Long idCategory);

  void delete(Long id);

}
