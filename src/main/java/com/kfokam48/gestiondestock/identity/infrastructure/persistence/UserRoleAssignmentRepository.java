package com.kfokam48.gestiondestock.identity.infrastructure.persistence;

import com.kfokam48.gestiondestock.identity.domain.model.UserRoleAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

  List<UserRoleAssignment> findAllByUserId(Long userId);

  List<UserRoleAssignment> findAllByRoleId(Long roleId);

}
