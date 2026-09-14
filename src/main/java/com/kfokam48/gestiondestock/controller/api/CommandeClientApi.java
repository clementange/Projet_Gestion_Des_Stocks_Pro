package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.APP_ROOT;

import com.kfokam48.gestiondestock.dto.CommandeClientDto;
import com.kfokam48.gestiondestock.dto.LigneCommandeClientDto;
import com.kfokam48.gestiondestock.model.EtatCommande;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "commandesclients")
public interface CommandeClientApi {

  @PostMapping(APP_ROOT + "/commandesclients/create")
  ResponseEntity<CommandeClientDto> save(@RequestBody CommandeClientDto dto);

  @PatchMapping(APP_ROOT + "/commandesclients/update/etat/{idCommande}/{etatCommande}")
  ResponseEntity<CommandeClientDto> updateEtatCommande(@PathVariable("idCommande") Long idCommande, @PathVariable("etatCommande") EtatCommande etatCommande);

  @PatchMapping(APP_ROOT + "/commandesclients/update/quantite/{idCommande}/{idLigneCommande}/{quantite}")
  ResponseEntity<CommandeClientDto> updateQuantiteCommande(@PathVariable("idCommande") Long idCommande,
      @PathVariable("idLigneCommande") Long idLigneCommande, @PathVariable("quantite") BigDecimal quantite);

  @PatchMapping(APP_ROOT + "/commandesclients/update/client/{idCommande}/{idClient}")
  ResponseEntity<CommandeClientDto> updateClient(@PathVariable("idCommande") Long idCommande, @PathVariable("idClient") Long idClient);

  @PatchMapping(APP_ROOT + "/commandesclients/update/article/{idCommande}/{idLigneCommande}/{idArticle}")
  ResponseEntity<CommandeClientDto> updateArticle(@PathVariable("idCommande") Long idCommande,
      @PathVariable("idLigneCommande") Long idLigneCommande, @PathVariable("idArticle") Long idArticle);

  @DeleteMapping(APP_ROOT + "/commandesclients/delete/article/{idCommande}/{idLigneCommande}")
  ResponseEntity<CommandeClientDto> deleteArticle(@PathVariable("idCommande") Long idCommande, @PathVariable("idLigneCommande") Long idLigneCommande);

  @GetMapping(APP_ROOT + "/commandesclients/{idCommandeClient}")
  ResponseEntity<CommandeClientDto> findById(@PathVariable("idCommandeClient") Long idCommandeClient);

  @GetMapping(APP_ROOT + "/commandesclients/filter/{codeCommandeClient}")
  ResponseEntity<CommandeClientDto> findByCode(@PathVariable("codeCommandeClient") String code);

  @GetMapping(APP_ROOT + "/commandesclients/all")
  ResponseEntity<List<CommandeClientDto>> findAll();

  @GetMapping(APP_ROOT + "/commandesclients/lignesCommande/{idCommande}")
  ResponseEntity<List<LigneCommandeClientDto>> findAllLignesCommandesClientByCommandeClientId(@PathVariable("idCommande") Long idCommande);

  @DeleteMapping(APP_ROOT + "/commandesclients/delete/{idCommandeClient}")
  ResponseEntity<Void> delete(@PathVariable("idCommandeClient") Long id);

}
