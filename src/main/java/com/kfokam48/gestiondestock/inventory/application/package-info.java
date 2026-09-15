/**
 * Facade applicative du module {@code inventory} : {@code InventoryFacade} est le point d'entree
 * unique utilise par purchasing, sales, transfers et reporting pour toute lecture/mutation de
 * stock (aucun autre module n'accede a {@code inventory.domain}/{@code infrastructure}).
 */
@org.springframework.modulith.NamedInterface("application")
package com.kfokam48.gestiondestock.inventory.application;
