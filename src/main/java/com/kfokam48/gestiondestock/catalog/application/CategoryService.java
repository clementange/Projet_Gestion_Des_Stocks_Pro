package com.kfokam48.gestiondestock.catalog.application;

import com.kfokam48.gestiondestock.catalog.application.dto.CategoryDto;
import java.util.List;

public interface CategoryService {

  CategoryDto save(CategoryDto dto);

  CategoryDto findById(Long id);

  CategoryDto findByCode(String code);

  List<CategoryDto> findAll();

  void delete(Long id);

}
