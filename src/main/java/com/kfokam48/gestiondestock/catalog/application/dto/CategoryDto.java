package com.kfokam48.gestiondestock.catalog.application.dto;

import com.kfokam48.gestiondestock.catalog.domain.model.Category;
import com.kfokam48.gestiondestock.organization.application.dto.OrganizationDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryDto {

  private Long id;

  private String code;

  private String designation;

  private Long idEntreprise;

  private OrganizationDto organization;

  @JsonIgnore
  private List<ArticleDto> articles;

  public static CategoryDto fromEntity(Category category) {
    if (category == null) {
      return null;
      // TODO throw an exception
    }

    return CategoryDto.builder()
        .id(category.getId())
        .code(category.getCode())
        .designation(category.getDesignation())
        .idEntreprise(category.getIdEntreprise())
        .organization(OrganizationDto.fromEntity(category.getOrganization()))
        .build();
  }

  public static Category toEntity(CategoryDto categoryDto) {
    if (categoryDto == null) {
      return null;
      // TODO throw an exception
    }

    Category category = new Category();
    category.setId(categoryDto.getId());
    category.setCode(categoryDto.getCode());
    category.setDesignation(categoryDto.getDesignation());
    category.setIdEntreprise(categoryDto.getIdEntreprise());
    category.setOrganization(OrganizationDto.toEntity(categoryDto.getOrganization()));

    return category;
  }
}
