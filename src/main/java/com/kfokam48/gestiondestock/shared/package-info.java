/**
 * Phase 4e : briques transverses extraites des paquets plats legacy {@code model/} et {@code dto/}.
 *
 * <p>Ces quatre types ne sont pas du legacy en extinction : ils sont utilises par TOUS les modules
 * et doivent le rester. {@code AbstractEntity} est la super-classe mappee de chaque entite JPA,
 * {@code Adresse} son embeddable d'adresse, {@code AdresseDto} sa projection HTTP (forme JSON
 * figee, contrat externe) et {@code ExtendedUser} le principal Spring Security resolu a
 * l'authentification, dont chaque adaptateur web a besoin pour obtenir {@code userId},
 * {@code idEntreprise} et {@code organizationId}.
 *
 * <p>Ils vivent dans la racine du paquet {@code shared} et non dans un sous-paquet : la racine
 * d'un module est son interface publique par defaut pour Spring Modulith, ce qui evite un
 * {@code @NamedInterface} sur une brique dont toute la raison d'etre est d'etre partagee.
 *
 * <p>Voir docs/api-migration-map.md.
 */
package com.kfokam48.gestiondestock.shared;
