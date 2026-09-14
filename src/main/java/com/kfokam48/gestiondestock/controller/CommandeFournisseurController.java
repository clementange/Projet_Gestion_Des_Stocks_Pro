package com.kfokam48.gestiondestock.controller;

import com.kfokam48.gestiondestock.controller.api.CommandeFournisseurApi;
import com.kfokam48.gestiondestock.dto.CommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import com.kfokam48.gestiondestock.services.CommandeFournisseurService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommandeFournisseurController implements CommandeFournisseurApi {

  private CommandeFournisseurService commandeFournisseurService;

  @Autowired
  public CommandeFournisseurController(CommandeFournisseurService commandeFournisseurService) {
    this.commandeFournisseurService = commandeFournisseurService;
  }

  @Override
  public CommandeFournisseurDto save(CommandeFournisseurDto dto) {
    return commandeFournisseurService.save(dto);
  }

  @Override
  public CommandeFournisseurDto updateEtatCommande(Long idCommande, EtatCommande etatCommande) {
    return commandeFournisseurService.updateEtatCommande(idCommande, etatCommande);
  }

  @Override
  public CommandeFournisseurDto updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite) {
    return commandeFournisseurService.updateQuantiteCommande(idCommande, idLigneCommande, quantite);
  }

  @Override
  public CommandeFournisseurDto updateFournisseur(Long idCommande, Long idFournisseur) {
    return commandeFournisseurService.updateFournisseur(idCommande, idFournisseur);
  }

  @Override
  public CommandeFournisseurDto updateArticle(Long idCommande, Long idLigneCommande, Long idArticle) {
    return commandeFournisseurService.updateArticle(idCommande, idLigneCommande, idArticle);
  }

  @Override
  public CommandeFournisseurDto deleteArticle(Long idCommande, Long idLigneCommande) {
    return commandeFournisseurService.deleteArticle(idCommande, idLigneCommande);
  }

  @Override
  public CommandeFournisseurDto findById(Long id) {
    return commandeFournisseurService.findById(id);
  }

  @Override
  public CommandeFournisseurDto findByCode(String code) {
    return commandeFournisseurService.findByCode(code);
  }

  @Override
  public List<CommandeFournisseurDto> findAll() {
    return commandeFournisseurService.findAll();
  }

  @Override
  public List<LigneCommandeFournisseurDto> findAllLignesCommandesFournisseurByCommandeFournisseurId(Long idCommande) {
    return commandeFournisseurService.findAllLignesCommandesFournisseurByCommandeFournisseurId(idCommande);
  }

  @Override
  public void delete(Long id) {
    commandeFournisseurService.delete(id);
  }
}
