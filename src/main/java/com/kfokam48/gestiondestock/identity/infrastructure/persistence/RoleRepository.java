package com.kfokam48.gestiondestock.identity.infrastructure.persistence;

import com.kfokam48.gestiondestock.identity.domain.model.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findRoleByCode(String code);

  List<Role> findAllByPermissions_Id(Long permissionId);

}
