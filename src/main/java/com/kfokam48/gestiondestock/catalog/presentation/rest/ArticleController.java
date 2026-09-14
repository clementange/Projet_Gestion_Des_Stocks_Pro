package com.kfokam48.gestiondestock.catalog.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneVenteDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "articles")
@RestController
public class ArticleController {

  private ArticleService articleService;

  @Autowired
  public ArticleController(ArticleService articleService) {
    this.articleService = articleService;
  }

  @PostMapping(value = APP_ROOT + "/articles/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto save(@RequestBody ArticleDto dto) {
    return articleService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/articles/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto findById(@PathVariable("idArticle") Long id) {
    return articleService.findById(id);
  }

  @GetMapping(value = APP_ROOT + "/articles/filter/{codeArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto findByCodeArticle(@PathVariable("codeArticle") String codeArticle) {
    return articleService.findByCodeArticle(codeArticle);
  }

  @GetMapping(value = APP_ROOT + "/articles/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ArticleDto> findAll() {
    return articleService.findAll();
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/vente/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneVenteDto> findHistoriqueVentes(@PathVariable("idArticle") Long idArticle) {
    return articleService.findHistoriqueVentes(idArticle);
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/commandeclient/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneCommandeClientDto> findHistoriaueCommandeClient(@PathVariable("idArticle") Long idArticle) {
    return articleService.findHistoriaueCommandeClient(idArticle);
  }

  @GetMapping(value = APP_ROOT + "/articles/historique/commandefournisseur/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<LigneCommandeFournisseurDto> findHistoriqueCommandeFournisseur(@PathVariable("idArticle") Long idArticle) {
    return articleService.findHistoriqueCommandeFournisseur(idArticle);
  }

  @GetMapping(value = APP_ROOT + "/articles/filter/category/{idCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ArticleDto> findAllArticleByIdCategory(@PathVariable("idCategory") Long idCategory) {
    return articleService.findAllArticleByIdCategory(idCategory);
  }

  @DeleteMapping(value = APP_ROOT + "/articles/delete/{idArticle}")
  public void delete(@PathVariable("idArticle") Long id) {
    articleService.delete(id);
  }
}
