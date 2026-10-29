package com.kfokam48.gestiondestock.validator;

import com.kfokam48.gestiondestock.exception.ErrorCodes;
import com.kfokam48.gestiondestock.exception.InvalidEntityException;
import java.util.List;

/**
 * Phase 5b-2f : point d'entree unique interdisant qu'un identifiant fourni par le client atteigne
 * un chemin de creation.
 *
 * <p>Tous les {@code toEntity} des DTO applicatifs propagent {@code dto.getId()}, et
 * {@code repository.save()} sur une entite portant un id deja existant est un MERGE JPA, donc une
 * mise a jour. Un endpoint de creation qui laisse passer cet id devient donc un endpoint de mise a
 * jour non authentifie au niveau metier : l'appelant ecrase une ressource existante, y compris
 * celle d'un autre tenant, et - lorsque le controleur force par ailleurs l'organisation depuis le
 * principal - se l'approprie au passage. Impact mesure sur {@code /sites/create} : le proprietaire
 * legitime recoit ensuite 404 sur sa propre ressource, l'attaquant 200.
 *
 * <p>Rejet explicite plutot que mise a {@code null} silencieuse : un appelant qui croyait faire une
 * mise a jour ne doit pas repartir convaincu d'avoir reussi alors qu'une ressource neuve vient
 * d'etre creee a cote. Mecanisme central plutot que recopie par service, pour que l'invariant
 * « un create n'est jamais un update » soit verifiable en un seul point - voir
 * {@code CreateEndpointIdGuardArchitectureTest} et docs/phase-5b2f-report.md.
 */
public final class CreationIdGuard {

  private CreationIdGuard() {
  }

  /**
   * @param id          l'identifiant porte par le DTO de creation, attendu {@code null}
   * @param entityLabel libelle metier de la ressource, injecte dans le message d'erreur
   * @throws InvalidEntityException ({@link ErrorCodes#CREATION_ID_FORBIDDEN}, HTTP 400) si un
   *                                identifiant a ete fourni
   */
  public static void requireNoId(Long id, String entityLabel) {
    if (id != null) {
      throw new InvalidEntityException(
          "La creation " + entityLabel + " n'accepte pas d'identifiant",
          ErrorCodes.CREATION_ID_FORBIDDEN,
          List.of("L'identifiant ne peut pas etre fourni a la creation"));
    }
  }
}
