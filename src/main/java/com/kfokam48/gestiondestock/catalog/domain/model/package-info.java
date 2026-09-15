/**
 * Modele de domaine expose du module {@code catalog} : {@code Article} est reference comme type
 * JPA (relation {@code @ManyToOne}) par d'autres modules (inventory.Stock/StockMovement,
 * purchasing.PurchaseOrderLine, sales.SaleLine/CustomerOrderLine) — pattern accepte en monolithe
 * modulaire partageant une seule base de donnees.
 */
@org.springframework.modulith.NamedInterface("domain-model")
package com.kfokam48.gestiondestock.catalog.domain.model;
