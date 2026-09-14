package com.kfokam48.gestiondestock.sales.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import com.kfokam48.gestiondestock.sales.domain.model.Customer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerDto {

  private Long id;

  private String nom;

  private String prenom;

  private AdresseDto adresse;

  private String photo;

  private String mail;

  private String numTel;

  private Long organizationId;

  public static CustomerDto fromEntity(Customer customer) {
    if (customer == null) {
      return null;
    }
    return CustomerDto.builder()
        .id(customer.getId())
        .nom(customer.getNom())
        .prenom(customer.getPrenom())
        .adresse(AdresseDto.fromEntity(customer.getAdresse()))
        .photo(customer.getPhoto())
        .mail(customer.getMail())
        .numTel(customer.getNumTel())
        .organizationId(customer.getOrganizationId())
        .build();
  }

  public static Customer toEntity(CustomerDto dto) {
    if (dto == null) {
      return null;
    }
    Customer customer = new Customer();
    customer.setId(dto.getId());
    customer.setNom(dto.getNom());
    customer.setPrenom(dto.getPrenom());
    customer.setAdresse(AdresseDto.toEntity(dto.getAdresse()));
    customer.setPhoto(dto.getPhoto());
    customer.setMail(dto.getMail());
    customer.setNumTel(dto.getNumTel());
    customer.setOrganizationId(dto.getOrganizationId());
    return customer;
  }

}
