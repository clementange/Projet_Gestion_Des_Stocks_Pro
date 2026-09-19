package com.kfokam48.gestiondestock.catalog.application;

import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import java.util.List;

public interface ArticleService {

  ArticleDto save(ArticleDto dto);

  ArticleDto findById(Long id);

  // Phase 5b-2a : variante utilisee par le point d'entree HTTP en lecture, qui verifie que
  // l'article appartient bien a l'organisation de l'appelant (memes 404 qu'un id inexistant en
  // cas de mismatch, pas de fuite d'existence) - voir docs/phase-5b2a-report.md.
  // findById(Long) sans organizationId reste utilise en interne (updatePhoto), pas expose tel
  // quel par un endpoint de lecture.
  ArticleDto findById(Long id, Long organizationId);

  ArticleDto findByCodeArticle(String codeArticle, Long organizationId);

  List<ArticleDto> findAll(Long organizationId);

  List<ArticleDto> findAllArticleByIdCategory(Long idCategory, Long organizationId);

  void delete(Long id);

  ArticleDto updatePhoto(Long id, String url);

}
