package com.kfokam48.gestiondestock;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;
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
 * packages legacy plats (model, services, dto, controller, validator, repository, exception,
 * handlers, interceptor, config, utils) qui n'ont jamais ete concus comme des modules — ce sont
 * des adaptateurs de compatibilite en cours d'extinction (voir CLAUDE.md). Sans exclusion, un run
 * brut de {@code ApplicationModules.of(ApiGestionDeStockApplication.class)} produit plus de 2000
 * lignes de "violations" qui sont en realite des cycles artificiels entre chaque module neuf et
 * ces packages legacy plats (ex: catalog -> dto -> catalog), sans rapport avec la question posee
 * par cette phase ("les 8 modules respectent-ils leurs frontieres entre eux ?"). Le detail complet
 * du run brut (avant exclusion) est archive dans docs/phase-1-report.md — cette classe teste la
 * version scopee aux 8 modules reels, qui est le signal exploitable au jour le jour.
 *
 * <p>Cette exclusion ne "corrige" aucune violation : elle formalise que les packages legacy plats
 * ne sont pas des modules, exactement comme CLAUDE.md le documente deja. Les violations reelles
 * entre les 8 modules (ex. catalog.application.impl.ArticleServiceImpl qui accede directement aux
 * repositories/entites internes de purchasing et sales) restent, elles, pleinement visibles et
 * NON corrigees ici (Phase 3).
 */
class ModularityTests {

  private static final Set<String> LEGACY_FLAT_PACKAGES = Set.of(
      "model", "services", "dto", "controller", "validator", "repository",
      "exception", "handlers", "interceptor", "config", "utils");

  private static final DescribedPredicate<JavaClass> EXCLUDE_LEGACY_FLAT_PACKAGES =
      new DescribedPredicate<>("legacy flat packages (Phase 4 extinction, pas des modules)") {
        @Override
        public boolean test(JavaClass javaClass) {
          String base = ApiGestionDeStockApplication.class.getPackageName() + ".";
          String pkg = javaClass.getPackageName();
          if (!pkg.startsWith(base)) {
            return false;
          }
          String rest = pkg.substring(base.length());
          String firstSegment = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
          return LEGACY_FLAT_PACKAGES.contains(firstSegment);
        }
      };

  ApplicationModules modules =
      ApplicationModules.of(ApiGestionDeStockApplication.class, EXCLUDE_LEGACY_FLAT_PACKAGES);

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
