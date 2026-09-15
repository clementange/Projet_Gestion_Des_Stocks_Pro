package com.kfokam48.gestiondestock.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.kfokam48.gestiondestock.ApiGestionDeStockApplication;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Phase 2 : traduit en tests qui cassent le build les regles d'architecture deja enoncees dans
 * CLAUDE.md (section "Regles d'architecture - zero tolerance"). Chaque regle est verifiee ici,
 * elle n'est PAS corrigee si elle echoue aujourd'hui — voir docs/migration-notes.md pour le detail
 * des violations trouvees et docs/phase-2-report.md pour le bilan de sortie de phase. La
 * correction (deplacement de code entre packages) est le perimetre explicite de la Phase 3.
 *
 * <p>Le decoupage module/legacy est partage avec {@code ModularityTests} (Phase 1) via
 * {@link ModuleBoundaries} : les packages legacy plats (model, services, dto, controller,
 * validator, repository, exception, handlers, interceptor, config, utils) ne sont jamais traites
 * comme des modules ici non plus.
 */
@AnalyzeClasses(packagesOf = ApiGestionDeStockApplication.class,
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  /**
   * {@code org.springframework.modulith} (l'annotation {@code @NamedInterface} posee en Phase 1
   * sur des package-info.java de domain.model) est volontairement exclu de la definition de
   * "framework Spring" ci-dessous : c'est une annotation qui decrit l'architecture d'un module,
   * elle ne couple le domaine a aucun comportement runtime Spring (pas d'injection, pas de proxy,
   * pas de contexte applicatif) — contrairement a org.springframework.beans/context/data/etc.
   */
  private static final DescribedPredicate<JavaClass> SPRING_FRAMEWORK_EXCLUDING_MODULITH_METADATA =
      JavaClass.Predicates.resideInAPackage("org.springframework..")
          .and(DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("org.springframework.modulith..")));

  /**
   * CLAUDE.md : "domain/ n'importe jamais Spring ni jakarta.persistence." Scinde en deux regles
   * distinctes (plutot qu'une seule regle combinee) pour que le rapport de violation dise
   * precisement laquelle des deux dependances interdites est en cause.
   */
  @ArchTest
  static final ArchRule domain_must_not_depend_on_spring_framework = noClasses()
      .that().resideInAPackage("..domain..")
      .should().dependOnClassesThat(SPRING_FRAMEWORK_EXCLUDING_MODULITH_METADATA)
      .as("le domaine ne doit dependre d'aucune classe du framework Spring (CLAUDE.md) - "
          + "org.springframework.modulith est exclu, voir commentaire ci-dessus");

  @ArchTest
  static final ArchRule domain_must_not_depend_on_jakarta_persistence = noClasses()
      .that().resideInAPackage("..domain..")
      .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..")
      .as("le domaine ne doit dependre d'aucune classe jakarta.persistence (CLAUDE.md)");

  /**
   * CLAUDE.md : "Injection par constructeur uniquement. Jamais @Autowired sur un champ." Regle
   * enoncee sans restriction de module dans CLAUDE.md -> appliquee a tout le code de production
   * (modules neufs ET legacy). Reutilise la regle prete a l'emploi d'ArchUnit
   * (@Autowired/@Inject/@Resource sur un champ).
   */
  @ArchTest
  static final ArchRule no_field_injection_anywhere = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  /**
   * CLAUDE.md : "Ne jamais appeler SecurityContextHolder depuis application/ ou domain/.
   * L'identite (userId, idEntreprise) est resolue dans l'adapter web (@AuthenticationPrincipal
   * ExtendedUser) et passee en parametre." Les packages legacy plats n'ont pas de sous-package
   * application/domain -> deja naturellement hors du perimetre de cette regle, pas besoin
   * d'exclusion explicite supplementaire.
   */
  @ArchTest
  static final ArchRule application_and_domain_must_not_use_security_context_holder = noClasses()
      .that().resideInAnyPackage("..application..", "..domain..")
      .should().dependOnClassesThat()
      .haveFullyQualifiedName("org.springframework.security.core.context.SecurityContextHolder")
      .as("application/ et domain/ ne doivent jamais appeler SecurityContextHolder ; l'identite "
          + "doit etre resolue dans l'adapter web et passee en parametre (CLAUDE.md)");

  /**
   * CLAUDE.md : "Erreurs metier via les exceptions dediees existantes... jamais une
   * RuntimeException generique." Regle formulee dans CLAUDE.md pour les modules refactores
   * specifiquement -> scopee a application/domain des 8 modules (pas au legacy plat, qui n'a de
   * toute facon pas de sous-package application/domain).
   */
  @ArchTest
  static final ArchRule application_and_domain_must_not_throw_generic_exceptions = noClasses()
      .that().resideInAnyPackage("..application..", "..domain..")
      .should(GeneralCodingRules.THROW_GENERIC_EXCEPTIONS)
      .as("application/ et domain/ ne doivent jamais lever Exception/RuntimeException/Throwable/"
          + "Error directement ; utiliser les exceptions dediees (EntityNotFoundException, "
          + "InvalidEntityException, InvalidOperationException) (CLAUDE.md)");

  /**
   * CLAUDE.md (implicite dans la definition meme de "module") : les 8 modules doivent former un
   * graphe de dependances sans cycle. Duplique volontairement une partie de ce que
   * {@code ModularityTests#verifiesModularStructure} verifie deja (via Spring Modulith), cette
   * fois avec l'API ArchUnit "slices" pure, sur le meme perimetre (les packages legacy plats sont
   * exclus des classes analysees avant de decouper en slices, sinon on retrouve exactement le
   * bruit documente en Phase 1 : chaque module neuf formant un faux cycle avec chaque package
   * legacy).
   */
  @ArchTest
  static void modules_must_be_free_of_cycles(JavaClasses classes) {
    JavaClasses moduleClasses = classes.that(DescribedPredicate.not(ModuleBoundaries.IN_LEGACY_FLAT_PACKAGE));
    slices()
        .matching(ApiGestionDeStockApplication.class.getPackageName() + ".(*)..")
        .should().beFreeOfCycles()
        .check(moduleClasses);
  }
}
