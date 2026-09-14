package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.RoleDto;
import java.util.List;

public interface RoleService {

  RoleDto save(RoleDto dto);

  RoleDto findById(Long id);

  RoleDto findByCode(String code);

  List<RoleDto> findAll();

  void delete(Long id);

}
