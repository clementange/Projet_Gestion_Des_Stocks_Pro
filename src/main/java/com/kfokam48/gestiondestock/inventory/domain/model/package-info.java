/**
 * Modele de domaine expose du module {@code inventory} : {@code StockMovementSource} (enum) est
 * reference par purchasing et sales pour qualifier l'origine d'un mouvement de stock qu'ils
 * declenchent via {@code InventoryFacade}.
 */
@org.springframework.modulith.NamedInterface("domain-model")
package com.kfokam48.gestiondestock.inventory.domain.model;
