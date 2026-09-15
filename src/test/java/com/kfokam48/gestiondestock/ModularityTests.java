package com.kfokam48.gestiondestock;

import com.kfokam48.gestiondestock.architecture.ModuleBoundaries;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Phase 1 : rend verifiable par un test les frontieres de module qui existent deja dans les faits
 * (catalog, identity, inventory, organization, purchasing, sales, transfers, reporting — la liste
 * de CLAUDE.md).
 *
 * <p>Spring Modulith traite par defaut CHAQUE sous-package direct du package de base comme un
 * module. Sous {@code com.kfokam48.gestiondestock} vivent, a cote des 8 modules ci-dessus, dix
 * packages legacy plats qui n'ont jamais ete concus comme des modules — voir
 * {@link ModuleBoundaries} pour le detail et la justification de l'exclusion (partagee avec
 * {@code ArchitectureRulesTest} depuis la Phase 2), et docs/phase-1-report.md pour le detail
 * complet des violations que le run brut (sans exclusion) fait remonter.
 *
 * <p>Cette exclusion ne "corrige" aucune violation : elle formalise que les packages legacy plats
 * ne sont pas des modules, exactement comme CLAUDE.md le documente deja. Les violations reelles
 * entre les 8 modules (ex. catalog.application.impl.ArticleServiceImpl qui accede directement aux
 * repositories/entites internes de purchasing et sales) restent, elles, pleinement visibles et
 * NON corrigees ici (Phase 3).
 */
class ModularityTests {

  ApplicationModules modules =
      ApplicationModules.of(ApiGestionDeStockApplication.class, ModuleBoundaries.IN_LEGACY_FLAT_PACKAGE);

  @Test
  void verifiesModularStructure() {
    modules.verify();
  }

  @Test
  void createModuleDocumentation() {
    new Documenter(modules)
        .writeDocumentation()
        .writeIndividualModulesAsPlantUml();
  }
}
