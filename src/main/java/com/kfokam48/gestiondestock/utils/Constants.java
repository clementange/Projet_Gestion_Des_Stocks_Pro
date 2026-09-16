package com.kfokam48.gestiondestock.utils;

public interface Constants {

  String APP_ROOT = "gestiondestock/v1";

  String COMMANDE_FOURNISSEUR_ENDPOINT = APP_ROOT + "/commandesfournisseurs";
  String CREATE_COMMANDE_FOURNISSEUR_ENDPOINT = COMMANDE_FOURNISSEUR_ENDPOINT + "/create";
  String FIND_COMMANDE_FOURNISSEUR_BY_ID_ENDPOINT = COMMANDE_FOURNISSEUR_ENDPOINT + "/{idCommandeFournisseur}";
  String FIND_COMMANDE_FOURNISSEUR_BY_CODE_ENDPOINT = COMMANDE_FOURNISSEUR_ENDPOINT + "/filter/{codeCommandeFournisseur}";
  String FIND_ALL_COMMANDE_FOURNISSEUR_ENDPOINT = COMMANDE_FOURNISSEUR_ENDPOINT + "/all";
  String DELETE_COMMANDE_FOURNISSEUR_ENDPOINT = COMMANDE_FOURNISSEUR_ENDPOINT + "/delete/{idCommandeFournisseur}";

  // Phase 4b : ENTREPRISE_ENDPOINT retire - /entreprises/create a disparu, successeur
  // /tenants/register (tenant.presentation.rest.TenantController), voir docs/phase-4b-report.md.

  String FOURNISSEUR_ENDPOINT = APP_ROOT + "/fournisseurs";

  String UTILISATEUR_ENDPOINT = APP_ROOT + "/utilisateurs";

  String VENTES_ENDPOINT = APP_ROOT + "/ventes";

  String AUTHENTICATION_ENDPOINT = APP_ROOT + "/auth";
}
