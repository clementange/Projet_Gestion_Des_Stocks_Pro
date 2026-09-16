package com.kfokam48.gestiondestock.catalog.application;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import java.util.List;

public interface ArticleService {

  ArticleDto save(ArticleDto dto);

  ArticleDto findById(Long id);

  ArticleDto findByCodeArticle(String codeArticle);

  List<ArticleDto> findAll();

  List<ArticleDto> findAllArticleByIdCategory(Long idCategory);

  void delete(Long id);

  ArticleDto updatePhoto(Long id, String url);

}
