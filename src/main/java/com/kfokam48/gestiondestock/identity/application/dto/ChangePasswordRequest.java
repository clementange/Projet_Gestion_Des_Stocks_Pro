package com.kfokam48.gestiondestock.identity.application.dto;

/**
 * DTO de commande, contrat neuf : record, conformement a CLAUDE.md ("les DTO de commande/lecture
 * applicatifs sont des record").
 */
public record ChangePasswordRequest(String motDePasse) {
}
