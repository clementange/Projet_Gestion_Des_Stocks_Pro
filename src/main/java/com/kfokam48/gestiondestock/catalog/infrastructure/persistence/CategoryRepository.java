package com.kfokam48.gestiondestock.catalog.infrastructure.persistence;

import com.kfokam48.gestiondestock.catalog.domain.model.Category;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  Optional<Category> findCategoryByCode(String code);

}
