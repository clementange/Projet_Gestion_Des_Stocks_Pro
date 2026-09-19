package com.kfokam48.gestiondestock.catalog.application.impl;

import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.catalog.application.validator.CategoryValidator;
import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.ArticleRepository;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.CategoryRepository;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import com.kfokam48.gestiondestock.exception.InvalidOperationException;
import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

  private CategoryRepository categoryRepository;
  private ArticleRepository articleRepository;

  @Autowired
  public CategoryServiceImpl(CategoryRepository categoryRepository, ArticleRepository articleRepository) {
    this.categoryRepository = categoryRepository;
    this.articleRepository = articleRepository;
  }

  @Override
  public CategoryDto save(CategoryDto dto) {
    List<String> errors = CategoryValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Article is not valid {}", dto);
      throw new InvalidEntityException("La category n'est pas valide", ErrorCodes.CATEGORY_NOT_VALID, errors);
    }
    return CategoryDto.fromEntity(
        categoryRepository.save(CategoryDto.toEntity(dto))
    );
  }

  @Override
  public CategoryDto findById(Long id, Long organizationId) {
    if (id == null) {
      log.error("Category ID is null");
      return null;
    }
    return categoryRepository.findById(id)
        .filter(category -> belongsToOrganization(category.getOrganization(), organizationId))
        .map(CategoryDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune category avec l'ID = " + id + " n' ete trouve dans la BDD",
            ErrorCodes.CATEGORY_NOT_FOUND)
        );
  }

  @Override
  public CategoryDto findByCode(String code, Long organizationId) {
    if (!StringUtils.hasLength(code)) {
      log.error("Category CODE is null");
      return null;
    }
    return categoryRepository.findCategoryByCode(code)
        .filter(category -> belongsToOrganization(category.getOrganization(), organizationId))
        .map(CategoryDto::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune category avec le CODE = " + code + " n' ete trouve dans la BDD",
            ErrorCodes.CATEGORY_NOT_FOUND)
        );
  }

  @Override
  public List<CategoryDto> findAll(Long organizationId) {
    return categoryRepository.findAll().stream()
        .filter(category -> belongsToOrganization(category.getOrganization(), organizationId))
        .map(CategoryDto::fromEntity)
        .collect(Collectors.toList());
  }

  // Phase 5b-2a : voir docs/phase-5b2a-report.md.
  private boolean belongsToOrganization(Organization organization, Long organizationId) {
    return organization != null && organization.getId() != null && organization.getId().equals(organizationId);
  }

  @Override
  public void delete(Long id) {
    if (id == null) {
      log.error("Category ID is null");
      return;
    }
    List<Article> articles = articleRepository.findAllByCategoryId(id);
    if (!articles.isEmpty()) {
      throw new InvalidOperationException("Impossible de supprimer cette categorie qui est deja utilise",
          ErrorCodes.CATEGORY_ALREADY_IN_USE);
    }
    categoryRepository.deleteById(id);
  }
}
