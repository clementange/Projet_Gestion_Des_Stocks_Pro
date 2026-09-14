package com.kfokam48.gestiondestock.dto;

import com.kfokam48.gestiondestock.model.Ventes;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VentesDto {

  private Long id;

  private String code;

  private Instant dateVente;

  private String commentaire;

  private List<LigneVenteDto> ligneVentes;

  private Long idEntreprise;

  public static VentesDto fromEntity(Ventes vente) {
    if (vente == null) {
      return null;
    }
    return VentesDto.builder()
        .id(vente.getId())
        .code(vente.getCode())
        .dateVente(vente.getDateVente())
        .commentaire(vente.getCommentaire())
        .idEntreprise(vente.getIdEntreprise())
        .build();
  }

  public static Ventes toEntity(VentesDto dto) {
    if (dto == null) {
      return null;
    }
    Ventes ventes = new Ventes();
    ventes.setId(dto.getId());
    ventes.setCode(dto.getCode());
    ventes.setDateVente(dto.getDateVente());
    ventes.setCommentaire(dto.getCommentaire());
    ventes.setIdEntreprise(dto.getIdEntreprise());
    return ventes;
  }
}
