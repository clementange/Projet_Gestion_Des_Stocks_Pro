package com.kfokam48.gestiondestock.repository;

import com.kfokam48.gestiondestock.model.Entreprise;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntrepriseRepository extends JpaRepository<Entreprise, Long> {

  Optional<Entreprise> findByOrganizationId(Long organizationId);

}
