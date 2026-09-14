package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.UserRoleAssignmentDto;
import java.util.List;

public interface UserRoleAssignmentService {

  UserRoleAssignmentDto save(UserRoleAssignmentDto dto);

  UserRoleAssignmentDto findById(Long id);

  List<UserRoleAssignmentDto> findAllByUser(Long userId);

  void delete(Long id);

}
