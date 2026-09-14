package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.PermissionDto;
import java.util.List;

public interface PermissionService {

  PermissionDto save(PermissionDto dto);

  PermissionDto findById(Long id);

  PermissionDto findByCode(String code);

  List<PermissionDto> findAll();

  void delete(Long id);

}
