package com.kfokam48.gestiondestock.transfers.domain.model;

/**
 * Cycle de vie de la section 15 du prompt maitre. EXPEDIE sort physiquement le stock du site
 * d'origine ; RECU l'entre au site de destination. Entre les deux, la marchandise est en
 * transport : ce n'est pas une seule operation atomique (contrairement a un simple ajustement),
 * c'est deux mouvements de stock distincts a deux moments distincts, chacun atomique
 * individuellement (verrouillage optimiste sur chaque Stock concerne).
 */
public enum TransferStatus {

  BROUILLON,
  DEMANDE,
  APPROUVE,
  EN_PREPARATION,
  EXPEDIE,
  RECU,
  ANNULE
}
