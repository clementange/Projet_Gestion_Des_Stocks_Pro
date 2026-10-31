package com.kfokam48.gestiondestock.catalog.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.model.auth.ExtendedUser;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import org.springframework.web.bind.annotation.PutMapping;
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

@Tag(name = "categories")
@RestController
public class CategoryController {

  private CategoryService categoryService;

  @Autowired
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  // Phase 5b-2a : organization forcee depuis l'appelant, jamais depuis le corps de la requete -
  // voir ArticleController.save et docs/phase-5b2a-report.md.
  @PostMapping(value = APP_ROOT + "/categories/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto save(@RequestBody CategoryDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    dto.setOrganization(OrganizationDto.builder().id(principal.getOrganizationId()).build());
    return categoryService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/categories/{idCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto findById(@PathVariable("idCategory") Long idCategory, @AuthenticationPrincipal ExtendedUser principal) {
    return categoryService.findById(idCategory, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/categories/filter/{codeCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto findByCode(@PathVariable("codeCategory") String codeCategory, @AuthenticationPrincipal ExtendedUser principal) {
    return categoryService.findByCode(codeCategory, principal.getOrganizationId());
  }

  @GetMapping(value = APP_ROOT + "/categories/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CategoryDto> findAll(@AuthenticationPrincipal ExtendedUser principal) {
    return categoryService.findAll(principal.getOrganizationId());
  }

  @DeleteMapping(value = APP_ROOT + "/categories/delete/{idCategory}")
  public void delete(@PathVariable("idCategory") Long id, @AuthenticationPrincipal ExtendedUser principal) {
    categoryService.delete(id, principal.getOrganizationId());
  }

  /**
   * Phase 5b-2g : seul chemin de mise a jour. L'id vient du CHEMIN ; le service verifie
   * l'appartenance a l'organisation de l'appelant avant toute ecriture et interdit tout
   * changement d'organisation - voir docs/phase-5b2g-report.md.
   */
  @PutMapping(value = APP_ROOT + "/categories/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto update(@PathVariable("id") Long id, @RequestBody CategoryDto dto, @AuthenticationPrincipal ExtendedUser principal) {
    return categoryService.update(id, dto, principal.getOrganizationId());
  }
}
