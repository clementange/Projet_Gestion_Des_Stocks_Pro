package com.kfokam48.gestiondestock.identity.infrastructure.persistence;

import com.kfokam48.gestiondestock.identity.domain.model.Permission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

  Optional<Permission> findPermissionByCode(String code);

}
