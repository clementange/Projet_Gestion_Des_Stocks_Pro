package com.kfokam48.gestiondestock.catalog.application.impl;

import com.kfokam48.gestiondestock.catalog.application.CategoryService;
import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import com.kfokam48.gestiondestock.catalog.application.validator.CategoryValidator;
import com.kfokam48.gestiondestock.catalog.domain.model.Article;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.ArticleRepository;
import com.kfokam48.gestiondestock.catalog.infrastructure.persistence.CategoryRepository;
import com.kfokam48.gestiondestock.exception.EntityNotFoundException;
import com.kfokam48.gestiondestock.catalog.domain.model.Category;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;
import com.kfokam48.gestiondestock.identity.application.AuthorizationService;
import com.kfokam48.gestiondestock.validator.CreationIdGuard;
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
  private AuthorizationService authorizationService;
  private ArticleRepository articleRepository;

  @Autowired
  public CategoryServiceImpl(CategoryRepository categoryRepository, ArticleRepository articleRepository,
      AuthorizationService authorizationService) {
    this.categoryRepository = categoryRepository;
    this.articleRepository = articleRepository;
    this.authorizationService = authorizationService;
  }

  @Override
  public CategoryDto save(CategoryDto dto, Long userId, Long organizationId) {
    requirePermission(userId, "CATEGORY_CREATE", organizationId);
    // Phase 5b-2g : un id fourni par le client ferait de ce create un MERGE JPA, donc
    // l'ecrasement d'une ressource existante - voir docs/phase-5b2g-report.md.
    CreationIdGuard.requireNoId(dto.getId(), "d'une categorie");
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

  // Phase 5b-2g : voir docs/phase-5b2g-report.md.
  @Override
  public CategoryDto update(Long id, CategoryDto dto, Long userId, Long organizationId) {
    Category existing = categoryRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune categorie avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CATEGORY_NOT_FOUND));
    if (organizationId == null || existing.getOrganization() == null
        || !organizationId.equals(existing.getOrganization().getId())) {
      log.warn("Category {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucune categorie avec l'ID = " + id + " n'a ete trouvee dans la BDD", ErrorCodes.CATEGORY_NOT_FOUND);
    }
    requirePermission(userId, "CATEGORY_UPDATE", organizationId);
    dto.setId(id);
    dto.setOrganization(OrganizationDto.fromEntity(existing.getOrganization()));
    List<String> errors = CategoryValidator.validate(dto);
    if (!errors.isEmpty()) {
      log.error("Category is not valid {}", dto);
      throw new InvalidEntityException("La category n'est pas valide", ErrorCodes.CATEGORY_NOT_VALID, errors);
    }
    existing.setCode(dto.getCode());
    existing.setDesignation(dto.getDesignation());
    return CategoryDto.fromEntity(categoryRepository.save(existing));
  }
  // Phase 5b-2h : voir docs/phase-5b2h-report.md.
  @Override
  public void delete(Long id, Long userId, Long organizationId) {
    if (id == null) {
      log.error("Category ID is null");
      return;
    }
    Category existing = categoryRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException(
            "Aucune categorie avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CATEGORY_NOT_FOUND));
    Long ownerOrg = existing.getOrganization() == null ? null : existing.getOrganization().getId();
    if (organizationId == null || ownerOrg == null || !organizationId.equals(ownerOrg)) {
      log.warn("Category {} does not belong to organization {}", id, organizationId);
      throw new EntityNotFoundException(
          "Aucune categorie avec l'ID = " + id + " n'a ete trouve dans la BDD", ErrorCodes.CATEGORY_NOT_FOUND);
    }
    // Phase 5c : appartenance (ci-dessus) -> permission -> ecriture.
    requirePermission(userId, "CATEGORY_DELETE", organizationId);
    delete(id);
  }
  /**
   * Phase 5c : ordre strict appartenance -> permission -> ecriture. L'appartenance est verifiee
   * en amont (5b-2g/2h) et renvoie un *_NOT_FOUND masque ; ici on repond a la question distincte
   * « qui, DANS l'organisation, a le droit de faire cela » - voir docs/rbac-matrix.md.
   */
  private void requirePermission(Long userId, String permissionCode, Long organizationId) {
    if (!authorizationService.hasPermission(userId, permissionCode, ScopeType.ORGANIZATION,
        organizationId, organizationId)) {
      log.warn("User {} lacks {} in organization {}", userId, permissionCode, organizationId);
      throw new InvalidOperationException(
          "Vous n'avez pas la permission d'effectuer cette action",
          ErrorCodes.CATEGORY_ACCESS_DENIED);
    }
  }
}
