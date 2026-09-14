package com.kfokam48.gestiondestock.interceptor;

import java.util.Set;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

public class Interceptor implements StatementInspector {

  /**
   * Entites legacy qui portent encore une colonne "identreprise". Les nouveaux modules
   * (organization, identity, catalog...) n'en ont pas : y appliquer ce filtre casserait leurs
   * requetes avec une colonne inexistante. Liste explicite (allowlist) plutot qu'une exclusion,
   * pour que toute nouvelle entite soit ignoree par defaut plutot que cassee par defaut.
   */
  private static final Set<String> TENANT_SCOPED_ENTITY_PREFIXES = Set.of(
      "article", "category", "mvtstk", "commandeclient", "commandefournisseur",
      "lignecommandeclient", "lignecommandefournisseur", "lignevente", "ventes", "utilisateur"
  );

  @Override
  public String inspect(String sql) {
    if (StringUtils.hasLength(sql) && sql.toLowerCase().startsWith("select") && sql.indexOf(".") > 7) {
      // select utilisateu0_.
      final String entityName = sql.substring(7, sql.indexOf("."));
      final String idEntreprise = MDC.get("idEntreprise");
      if (StringUtils.hasLength(entityName)
          && isTenantScopedEntity(entityName)
          && StringUtils.hasLength(idEntreprise)) {

        if (sql.contains("where")) {
          sql = sql + " and " + entityName + ".identreprise = " + idEntreprise;
        } else {
          sql = sql + " where " + entityName + ".identreprise = " + idEntreprise;
        }
      }
    }
    return sql;
  }

  private boolean isTenantScopedEntity(String entityName) {
    String lowerCaseEntityName = entityName.toLowerCase();
    return TENANT_SCOPED_ENTITY_PREFIXES.stream().anyMatch(lowerCaseEntityName::startsWith);
  }
}
