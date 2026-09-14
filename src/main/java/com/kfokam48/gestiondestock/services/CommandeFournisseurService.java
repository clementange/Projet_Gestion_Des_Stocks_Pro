package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.CommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import java.math.BigDecimal;
import java.util.List;

public interface CommandeFournisseurService {

  CommandeFournisseurDto save(CommandeFournisseurDto dto);

  CommandeFournisseurDto updateEtatCommande(Long idCommande, EtatCommande etatCommande);

  CommandeFournisseurDto updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite);

  CommandeFournisseurDto updateFournisseur(Long idCommande, Long idFournisseur);

  CommandeFournisseurDto updateArticle(Long idCommande, Long idLigneCommande, Long idArticle);

  // Delete article ==> delete LigneCommandeFournisseur
  CommandeFournisseurDto deleteArticle(Long idCommande, Long idLigneCommande);

  CommandeFournisseurDto findById(Long id);

  CommandeFournisseurDto findByCode(String code);

  List<CommandeFournisseurDto> findAll();

  List<LigneCommandeFournisseurDto> findAllLignesCommandesFournisseurByCommandeFournisseurId(Long idCommande);

  void delete(Long id);

}
