package com.kfokam48.gestiondestock.controller;

import com.kfokam48.gestiondestock.controller.api.CommandeClientApi;
import com.kfokam48.gestiondestock.dto.CommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import com.kfokam48.gestiondestock.services.CommandeClientService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommandeClientController implements CommandeClientApi {

  private CommandeClientService commandeClientService;

  @Autowired
  public CommandeClientController(CommandeClientService commandeClientService) {
    this.commandeClientService = commandeClientService;
  }

  @Override
  public ResponseEntity<CommandeClientDto> save(CommandeClientDto dto) {
    return ResponseEntity.ok(commandeClientService.save(dto));
  }

  @Override
  public ResponseEntity<CommandeClientDto> updateEtatCommande(Long idCommande, EtatCommande etatCommande) {
    return ResponseEntity.ok(commandeClientService.updateEtatCommande(idCommande, etatCommande));
  }

  @Override
  public ResponseEntity<CommandeClientDto> updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite) {
    return ResponseEntity.ok(commandeClientService.updateQuantiteCommande(idCommande, idLigneCommande, quantite));
  }

  @Override
  public ResponseEntity<CommandeClientDto> updateClient(Long idCommande, Long idClient) {
    return ResponseEntity.ok(commandeClientService.updateClient(idCommande, idClient));
  }

  @Override
  public ResponseEntity<CommandeClientDto> updateArticle(Long idCommande, Long idLigneCommande, Long idArticle) {
    return ResponseEntity.ok(commandeClientService.updateArticle(idCommande, idLigneCommande, idArticle));
  }

  @Override
  public ResponseEntity<CommandeClientDto> deleteArticle(Long idCommande, Long idLigneCommande) {
    return ResponseEntity.ok(commandeClientService.deleteArticle(idCommande, idLigneCommande));
  }

  @Override
  public ResponseEntity<CommandeClientDto> findById(Long id) {
    return ResponseEntity.ok(commandeClientService.findById(id));
  }

  @Override
  public ResponseEntity<CommandeClientDto> findByCode(String code) {
    return ResponseEntity.ok(commandeClientService.findByCode(code));
  }

  @Override
  public ResponseEntity<List<CommandeClientDto>> findAll() {
    return ResponseEntity.ok(commandeClientService.findAll());
  }

  @Override
  public ResponseEntity<List<LigneCommandeClientDto>> findAllLignesCommandesClientByCommandeClientId(Long idCommande) {
    return ResponseEntity.ok(commandeClientService.findAllLignesCommandesClientByCommandeClientId(idCommande));
  }

  @Override
  public ResponseEntity<Void> delete(Long id) {
    commandeClientService.delete(id);
    return ResponseEntity.ok().build();
  }
}
