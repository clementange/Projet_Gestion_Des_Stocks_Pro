/**
 * Surface publique (DTO) du module {@code identity} : {@code UserDto}/{@code UserRoleAssignmentDto}/
 * {@code RoleDto} sont legitimement references par {@code tenant} (orchestration d'inscription,
 * voir docs/phase-4b-report.md). Ecart pre-existant a la Phase 4b : ce package n'avait jamais eu
 * besoin d'etre expose explicitement jusqu'ici, seul du code legacy plat (hors perimetre
 * ArchUnit/Modulith) appelait ces types directement ; {@code tenant} est le premier module
 * reellement enregistre a le faire.
 */
@org.springframework.modulith.NamedInterface("dto")
package com.kfokam48.gestiondestock.identity.application.dto;
