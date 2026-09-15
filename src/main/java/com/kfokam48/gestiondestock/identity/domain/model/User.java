package com.kfokam48.gestiondestock.identity.domain.model;

import com.kfokam48.gestiondestock.model.AbstractEntity;
import com.kfokam48.gestiondestock.model.Adresse;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Phase 4a : identite reelle de l'application (login, mot de passe, email) - anciennement
 * {@code model.Utilisateur}. Aucun invariant metier : le hachage du mot de passe (PasswordEncoder,
 * bean Spring) et l'unicite d'email (necessite un acces repository) sont des preoccupations
 * applicatives, pas des regles portees par cet agregat - voir {@code identity.application.UserService}.
 *
 * <p>{@code idEntreprise} est une reference faible (pas de {@code @ManyToOne}) : {@code Entreprise}
 * n'est pas encore migree (Phase 4b), meme pattern que {@code purchasing.PurchaseOrder.supplierId}.
 *
 * <p>Mapping JPA declare dans META-INF/orm.xml, pas en annotations - voir le commentaire en tete
 * de ce fichier XML.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends AbstractEntity {

  private String nom;

  private String prenom;

  private String email;

  private Instant dateDeNaissance;

  private String motDePasse;

  private Adresse adresse;

  private String photo;

  private Long idEntreprise;

}
