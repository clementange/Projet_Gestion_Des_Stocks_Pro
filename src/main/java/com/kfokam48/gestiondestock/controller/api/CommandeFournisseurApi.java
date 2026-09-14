package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.COMMANDE_FOURNISSEUR_ENDPOINT;
import static com.kfokam48.gestiondestock.utils.Constants.CREATE_COMMANDE_FOURNISSEUR_ENDPOINT;
import static com.kfokam48.gestiondestock.utils.Constants.DELETE_COMMANDE_FOURNISSEUR_ENDPOINT;
import static com.kfokam48.gestiondestock.utils.Constants.FIND_ALL_COMMANDE_FOURNISSEUR_ENDPOINT;
import static com.kfokam48.gestiondestock.utils.Constants.FIND_COMMANDE_FOURNISSEUR_BY_CODE_ENDPOINT;
import static com.kfokam48.gestiondestock.utils.Constants.FIND_COMMANDE_FOURNISSEUR_BY_ID_ENDPOINT;

import com.kfokam48.gestiondestock.dto.CommandeFournisseurDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeFournisseurDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "commandefournisseur")
public interface CommandeFournisseurApi {

  @PostMapping(CREATE_COMMANDE_FOURNISSEUR_ENDPOINT)
  CommandeFournisseurDto save(@RequestBody CommandeFournisseurDto dto);

  @PatchMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/update/etat/{idCommande}/{etatCommande}")
  CommandeFournisseurDto updateEtatCommande(@PathVariable("idCommande") Long idCommande, @PathVariable("etatCommande") EtatCommande etatCommande);

  @PatchMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/update/quantite/{idCommande}/{idLigneCommande}/{quantite}")
  CommandeFournisseurDto updateQuantiteCommande(@PathVariable("idCommande") Long idCommande,
      @PathVariable("idLigneCommande") Long idLigneCommande, @PathVariable("quantite") BigDecimal quantite);

  @PatchMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/update/fournisseur/{idCommande}/{idFournisseur}")
  CommandeFournisseurDto updateFournisseur(@PathVariable("idCommande") Long idCommande, @PathVariable("idFournisseur") Long idFournisseur);

  @PatchMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/update/article/{idCommande}/{idLigneCommande}/{idArticle}")
  CommandeFournisseurDto updateArticle(@PathVariable("idCommande") Long idCommande,
      @PathVariable("idLigneCommande") Long idLigneCommande, @PathVariable("idArticle") Long idArticle);

  @DeleteMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/delete/article/{idCommande}/{idLigneCommande}")
  CommandeFournisseurDto deleteArticle(@PathVariable("idCommande") Long idCommande, @PathVariable("idLigneCommande") Long idLigneCommande);

  @GetMapping(FIND_COMMANDE_FOURNISSEUR_BY_ID_ENDPOINT)
  CommandeFournisseurDto findById(@PathVariable("idCommandeFournisseur") Long id);

  @GetMapping(FIND_COMMANDE_FOURNISSEUR_BY_CODE_ENDPOINT)
  CommandeFournisseurDto findByCode(@PathVariable("codeCommandeFournisseur") String code);

  @GetMapping(FIND_ALL_COMMANDE_FOURNISSEUR_ENDPOINT)
  List<CommandeFournisseurDto> findAll();

  @GetMapping(COMMANDE_FOURNISSEUR_ENDPOINT + "/lignesCommande/{idCommande}")
  List<LigneCommandeFournisseurDto> findAllLignesCommandesFournisseurByCommandeFournisseurId(@PathVariable("idCommande") Long idCommande);

  @DeleteMapping(DELETE_COMMANDE_FOURNISSEUR_ENDPOINT)
  void delete(@PathVariable("idCommandeFournisseur") Long id);

}
