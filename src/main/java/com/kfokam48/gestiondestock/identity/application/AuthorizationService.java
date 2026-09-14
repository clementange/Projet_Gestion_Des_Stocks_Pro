package com.kfokam48.gestiondestock.identity.application;

import com.kfokam48.gestiondestock.identity.domain.model.ScopeType;

/**
 * Point d'entree unique pour repondre a : "l'utilisateur X peut-il executer PERMISSION sur la
 * ressource de type SCOPE_TYPE / SCOPE_ID ?" (section 37 du prompt maitre).
 *
 * <p>Un utilisateur avec une affectation GLOBAL possede la permission partout, quel que soit le
 * perimetre demande (Directeur General). Une affectation non-GLOBAL ne s'applique que si son
 * type et son identifiant de ressource correspondent exactement a ceux demandes.
 *
 * <p>La resolution hierarchique (ex: un responsable de region avec un scope CITY voit aussi les
 * sites de cette ville) n'est PAS geree ici : aucun cas d'usage concret ne l'exige encore. Elle
 * sera ajoutee, via une dependance explicite vers le module organization, quand un module
 * consommateur (reporting, inventory...) en aura reellement besoin.
 */
public interface AuthorizationService {

  boolean hasPermission(Long userId, String permissionCode, ScopeType scopeType, Long scopeId);

  boolean hasGlobalAccess(Long userId);

}
