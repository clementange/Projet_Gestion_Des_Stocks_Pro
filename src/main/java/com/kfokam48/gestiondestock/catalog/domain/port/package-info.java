/**
 * Ports du module {@code catalog} : interfaces que {@code catalog} declare et que d'autres modules
 * implementent (inversion de dependance). Expose comme interface nommee pour que le module
 * {@code legacy} puisse fournir son adaptateur — voir {@link
 * com.kfokam48.gestiondestock.catalog.domain.port.ArticleUsagePort}.
 */
@org.springframework.modulith.NamedInterface("domain-port")
package com.kfokam48.gestiondestock.catalog.domain.port;
