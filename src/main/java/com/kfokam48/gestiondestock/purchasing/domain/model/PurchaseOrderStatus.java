package com.kfokam48.gestiondestock.purchasing.domain.model;

/**
 * Cycle de vie simplifie par rapport au "VALIDATION -> EXPEDITION -> RECEPTION" de la section 14
 * du prompt maitre : l'expedition par le fournisseur n'est pas un evenement que cette
 * application peut observer ou controler, l'ajouter sans logique metier reelle serait un etat
 * mort. Ce qui compte, et qui est preserve ici, c'est que l'entree en stock ne soit declenchee
 * que par RECUE, jamais par la creation ou la validation de la commande.
 */
public enum PurchaseOrderStatus {

  BROUILLON,
  VALIDEE,
  RECUE,
  ANNULEE
}
