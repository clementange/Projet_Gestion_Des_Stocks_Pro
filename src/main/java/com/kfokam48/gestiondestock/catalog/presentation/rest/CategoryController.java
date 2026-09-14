package com.kfokam48.gestiondestock.catalog.presentation.rest;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
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

@Tag(name = "categories")
@RestController
public class CategoryController {

  private CategoryService categoryService;

  @Autowired
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @PostMapping(value = APP_ROOT + "/categories/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto save(@RequestBody CategoryDto dto) {
    return categoryService.save(dto);
  }

  @GetMapping(value = APP_ROOT + "/categories/{idCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto findById(@PathVariable("idCategory") Long idCategory) {
    return categoryService.findById(idCategory);
  }

  @GetMapping(value = APP_ROOT + "/categories/filter/{codeCategory}", produces = MediaType.APPLICATION_JSON_VALUE)
  public CategoryDto findByCode(@PathVariable("codeCategory") String codeCategory) {
    return categoryService.findByCode(codeCategory);
  }

  @GetMapping(value = APP_ROOT + "/categories/all", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CategoryDto> findAll() {
    return categoryService.findAll();
  }

  @DeleteMapping(value = APP_ROOT + "/categories/delete/{idCategory}")
  public void delete(@PathVariable("idCategory") Long id) {
    categoryService.delete(id);
  }
}
