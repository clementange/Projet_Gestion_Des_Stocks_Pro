/**
 * Modele de domaine expose du module {@code organization} : {@code Site}/{@code Organization}
 * sont references comme types JPA (relations {@code @ManyToOne}) par catalog, inventory,
 * purchasing, sales et transfers.
 */
@org.springframework.modulith.NamedInterface("domain-model")
package com.kfokam48.gestiondestock.organization.domain.model;
