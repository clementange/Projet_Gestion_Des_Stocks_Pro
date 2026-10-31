/**
 * Facades applicatives du module {@code catalog}. Phase 5b-2h : {@code ArticleService} est appele
 * par les controleurs d'historique d'article de {@code sales} et {@code purchasing} pour verifier
 * que l'article demande appartient bien a l'organisation de l'appelant avant de renvoyer son
 * historique - sans quoi l'historique d'un article d'un autre tenant etait lisible. Exposer ce
 * package en interface nommee est la facon prevue par Spring Modulith de rendre cet appel legitime,
 * plutot que de laisser un module toucher un type non expose - voir docs/phase-5b2h-report.md.
 */
@org.springframework.modulith.NamedInterface("application")
package com.kfokam48.gestiondestock.catalog.application;
