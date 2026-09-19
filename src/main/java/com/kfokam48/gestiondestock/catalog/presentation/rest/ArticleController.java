package com.kfokam48.gestiondestock.catalog.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.catalog.application.ArticleService;
import com.kfokam48.gestiondestock.catalog.application.dto.ArticleDto;
import com.kfokam48.gestiondestock.media.application.dto.PhotoUrlRequest;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Phase 3a : les endpoints /articles/historique/{vente,commandeclient,commandefournisseur}/{id}
// ont ete deplaces vers sales.presentation.rest.legacy.ArticleHistoryLegacyController et
// purchasing.presentation.rest.legacy.ArticleHistoryLegacyController (URL HTTP inchangees) pour
// eliminer un cycle catalog <-> sales/purchasing (voir docs/phase-3a-report.md).
@Tag(name = "articles")
@RestController
public class ArticleController {

  private ArticleService articleService;

  @Autowired
  public ArticleController(ArticleService articleService) {
    this.articleService = articleService;
  }

  // Phase 5b-2a : organization forcee depuis l'appelant, jamais depuis le corps de la requete -
  // sinon un article cree sans organization explicite devient invisible via les lectures
  // desormais filtrees par organisation, et un appelant malveillant pourrait sinon injecter des
  // donnees dans une AUTRE organisation. Voir docs/phase-5b2a-report.md.
  @PostMapping(value = APP_ROOT + "/articles/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto save(@RequestBody ArticleDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    dto.setOrganization(OrganizationDto.builder().id(principal.getOrganizationId()).build());
    return articleService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/articles/{idArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto findById(@PathVariable("idArticle") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    return articleService.findById(id, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/articles/filter/{codeArticle}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto findByCodeArticle(@PathVariable("codeArticle") String codeArticle, @AuthenticationPrincipal ExtendedUser principal) {
    return articleService.findByCodeArticle(codeArticle, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/articles/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ArticleDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return articleService.findAll(principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/articles/filter/category/{idCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<ArticleDto> findAllArticleByIdCategory(@PathVariable("idCategory") Long idCategory, @AuthenticationPrincipal ExtendedUser principal) {
    return articleService.findAllArticleByIdCategory(idCategory, principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/articles/delete/{idArticle}")
  public void delete(@PathVariable("idArticle") Long id) {
    articleService.delete(id);
  }

  // Phase 4c : remplace /save/{id}/{title}/article (StrategyPhotoContext, supprime). L'appelant
  // uploade d'abord via POST /media/upload puis attache l'URL ici.
  @PostMapping(value = APP_ROOT + "/articles/{idArticle}/photo", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ArticleDto updatePhoto(@PathVariable("idArticle") Long id, @RequestBody PhotoUrlRequest request) {
    return articleService.updatePhoto(id, request.url());
  }
}
