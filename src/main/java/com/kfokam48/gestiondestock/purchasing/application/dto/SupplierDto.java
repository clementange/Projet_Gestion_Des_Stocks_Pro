package com.kfokam48.gestiondestock.purchasing.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.purchasing.domain.model.Supplier;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierDto {

  private Long id;

  private String nom;

  private String prenom;

  private AdresseDto adresse;

  private String photo;

  private String mail;

  private String numTel;

  private Long organizationId;

  public static SupplierDto fromEntity(Supplier supplier) {
    if (supplier == null) {
      return null;
    }
    return SupplierDto.builder()
        .id(supplier.getId())
        .nom(supplier.getNom())
        .prenom(supplier.getPrenom())
        .adresse(AdresseDto.fromEntity(supplier.getAdresse()))
        .photo(supplier.getPhoto())
        .mail(supplier.getMail())
        .numTel(supplier.getNumTel())
        .organizationId(supplier.getOrganizationId())
        .build();
  }

  public static Supplier toEntity(SupplierDto dto) {
    if (dto == null) {
      return null;
    }
    Supplier supplier = new Supplier();
    supplier.setId(dto.getId());
    supplier.setNom(dto.getNom());
    supplier.setPrenom(dto.getPrenom());
    supplier.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    supplier.setPhoto(dto.getPhoto());
    supplier.setMail(dto.getMail());
    supplier.setNumTel(dto.getNumTel());
    supplier.setOrganizationId(dto.getOrganizationId());
    return supplier;
  }

}
