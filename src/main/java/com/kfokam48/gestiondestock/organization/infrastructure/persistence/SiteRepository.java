package com.kfokam48.gestiondestock.organization.infrastructure.persistence;

import com.kfokam48.gestiondestock.organization.domain.model.Site;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

  Optional<Site> findSiteByCode(String code);

  List<Site> findAllByCityId(Long cityId);

}
