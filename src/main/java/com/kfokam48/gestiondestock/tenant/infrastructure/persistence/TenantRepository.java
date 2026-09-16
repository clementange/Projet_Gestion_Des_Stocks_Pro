package com.kfokam48.gestiondestock.tenant.infrastructure.persistence;

import com.kfokam48.gestiondestock.tenant.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

}
