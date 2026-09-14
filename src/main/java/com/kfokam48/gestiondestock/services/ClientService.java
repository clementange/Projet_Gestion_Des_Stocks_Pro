package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.ClientDto;
import java.util.List;

public interface ClientService {

  ClientDto save(ClientDto dto);

  ClientDto findById(Long id);

  List<ClientDto> findAll();

  void delete(Long id);

}
