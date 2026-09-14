package com.kfokam48.gestiondestock.sales.domain.model;

/**
 * Cycle de vie de la commande client (Livrable 7) : la reservation du stock (RESERVEE) evite
 * qu'une commande validee se retrouve sans stock au moment de la preparation ; la sortie
 * physique et la liberation de la reservation n'interviennent qu'a la livraison (LIVREE).
 */
public enum CustomerOrderStatus {

  BROUILLON,
  VALIDEE,
  RESERVEE,
  PREPAREE,
  EXPEDIEE,
  LIVREE,
  ANNULEE
}
