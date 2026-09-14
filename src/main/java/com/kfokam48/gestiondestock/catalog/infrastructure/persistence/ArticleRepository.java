package com.kfokam48.gestiondestock.catalog.infrastructure.persistence;

import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {

  Optional<Article> findArticleByCodeArticle(String codeArticle);

  List<Article> findAllByCategoryId(Long idCategory);

}
