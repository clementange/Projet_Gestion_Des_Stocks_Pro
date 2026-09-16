package com.kfokam48.gestiondestock.tenant.application.dto;

import com.kfokam48.gestiondestock.dto.AdresseDto;
import java.time.Instant;

/**
 * DTO de commande, contrat neuf sans contrainte de compatibilite : record, conformement a
 * CLAUDE.md. Champs admin explicites (adminNom/adminPrenom/adminEmail/adminDateDeNaissance)
 * plutot que de reprendre les champs de l'entreprise pour l'identite de l'administrateur, comme
 * le faisait l'ancien EntrepriseServiceImpl.fromEntreprise() (prenom(dto.getCodeFiscal())) - voir
 * docs/phase-4b-report.md.
 */
public record TenantRegistrationRequest(
    String nom,
    String description,
    AdresseDto adresse,
    String codeFiscal,
    String photo,
    String email,
    String numTel,
    String steWeb,
    String adminNom,
    String adminPrenom,
    String adminEmail,
    Instant adminDateDeNaissance,
    String motDePasse,
    String confirmMotDePasse) {
}
