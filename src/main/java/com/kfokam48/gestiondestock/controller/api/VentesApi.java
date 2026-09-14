package com.kfokam48.gestiondestock.controller.api;

import static com.kfokam48.gestiondestock.utils.Constants.VENTES_ENDPOINT;

import com.kfokam48.gestiondestock.dto.VentesDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "ventes")
public interface VentesApi {

  @PostMapping(VENTES_ENDPOINT + "/create")
  VentesDto save(@RequestBody VentesDto dto);

  @GetMapping(VENTES_ENDPOINT + "/{idVente}")
  VentesDto findById(@PathVariable("idVente") Long id);

  // "/{codeVente}" collisionnait avec "/{idVente}" (findById) : meme forme de route, Spring
  // route toujours vers le meme des deux handlers enregistres en premier, rendant l'autre
  // inatteignable. Prefixe "filter/", comme dans tous les autres modules (Client/Fournisseur/
  // Article/Category/...), pour lever l'ambiguite.
  @GetMapping(VENTES_ENDPOINT + "/filter/{codeVente}")
  VentesDto findByCode(@PathVariable("codeVente") String code);

  @GetMapping(VENTES_ENDPOINT + "/all")
  List<VentesDto> findAll();

  @DeleteMapping(VENTES_ENDPOINT + "/delete/{idVente}")
  void delete(@PathVariable("idVente") Long id);

}
