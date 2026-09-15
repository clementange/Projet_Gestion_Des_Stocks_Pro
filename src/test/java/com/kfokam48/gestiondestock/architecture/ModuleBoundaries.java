package com.kfokam48.gestiondestock.architecture;

import com.kfokam48.gestiondestock.ApiGestionDeStockApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import java.util.Set;

/**
 * Source unique de verite pour le decoupage module/legacy valide en Phase 1, partagee entre
 * {@code ModularityTests} (Spring Modulith) et {@code ArchitectureRulesTest} (ArchUnit) : les deux
 * s'appuient sur le meme {@link DescribedPredicate}, puisque {@code ApplicationModules.of(Class,
 * DescribedPredicate)} de Spring Modulith accepte directement un predicat ArchUnit.
 *
 * <p>Les dix packages listes ici (model, services, dto, controller, validator, repository,
 * exception, handlers, interceptor, config, utils) sont les adaptateurs de compatibilite legacy
 * decrits dans CLAUDE.md — jamais concus comme des modules, en cours d'extinction (Phase 4). Sans
 * cette exclusion, Spring Modulith/ArchUnit les traitent par defaut comme des modules a part
 * entiere et produisent des cycles/violations artificiels sans rapport avec les regles verifiees
 * ici (voir docs/phase-1-report.md pour le detail du run brut).
 */
public final class ModuleBoundaries {

  private ModuleBoundaries() {
  }

  public static final Set<String> LEGACY_FLAT_PACKAGES = Set.of(
      "model", "services", "dto", "controller", "validator", "repository",
      "exception", "handlers", "interceptor", "config", "utils");

  public static final DescribedPredicate<JavaClass> IN_LEGACY_FLAT_PACKAGE =
      new DescribedPredicate<>("dans un package legacy plat (pas un module)") {
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
}
