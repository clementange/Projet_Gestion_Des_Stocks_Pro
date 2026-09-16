package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.application.dto.UserDto;
import java.util.List;

public interface UserService {

  UserDto save(UserDto dto);

  UserDto findById(Long id);

  UserDto findByEmail(String email);

  List<UserDto> findAll();

  void delete(Long id);

  UserDto changePassword(Long id, String rawPassword);

  UserDto updatePhoto(Long id, String url);

}
