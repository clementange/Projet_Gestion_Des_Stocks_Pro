package com.kfokam48.gestiondestock.services;

import com.kfokam48.gestiondestock.dto.CommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import java.math.BigDecimal;
import java.util.List;

public interface CommandeClientService {

  CommandeClientDto save(CommandeClientDto dto);

  CommandeClientDto updateEtatCommande(Long idCommande, EtatCommande etatCommande);

  CommandeClientDto updateQuantiteCommande(Long idCommande, Long idLigneCommande, BigDecimal quantite);

  CommandeClientDto updateClient(Long idCommande, Long idClient);

  CommandeClientDto updateArticle(Long idCommande, Long idLigneCommande, Long newIdArticle);

  // Delete article ==> delete LigneCommandeClient
  CommandeClientDto deleteArticle(Long idCommande, Long idLigneCommande);

  CommandeClientDto findById(Long id);

  CommandeClientDto findByCode(String code);

  List<CommandeClientDto> findAll();

  List<LigneCommandeClientDto> findAllLignesCommandesClientByCommandeClientId(Long idCommande);

  void delete(Long id);

}
