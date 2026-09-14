package com.kfokam48.gestiondestock.organization.infrastructure.persistence;

import com.kfokam48.gestiondestock.organization.domain.model.City;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {

  List<City> findAllByOrganizationId(Long organizationId);

}
