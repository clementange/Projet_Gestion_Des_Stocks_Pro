package com.kfokam48.gestiondestock.organization.infrastructure.persistence;

import com.kfokam48.gestiondestock.organization.domain.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

}
