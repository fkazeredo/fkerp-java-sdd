package com.fksoft.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Architecture gates enforced by ArchUnit (SPEC-0001, ADR 0012/0013). These rules run in the normal
 * test suite and must never be weakened to make code pass. The companion {@link
 * ArchitectureRulesHaveTeethTest} proves the layer rule actually fails when a boundary is violated.
 *
 * <p>Note on the entity rules: Lombok's {@code @Data}/{@code @Setter} are
 * {@code @Retention(SOURCE)} and thus invisible to bytecode analysis (ArchUnit rejects checking
 * them directly), and with fluent accessors a Lombok setter is not even named {@code setX}. So the
 * no-mutation invariant is enforced by their observable effect: no JavaBean setters on entities,
 * and no Lombok-generated mutators (detected via the CLASS-retention {@code @lombok.Generated},
 * enabled in {@code lombok.config}).
 */
@AnalyzeClasses(packages = "com.fksoft", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  /** The pure domain core must not depend on delivery (application) or infra (ADR 0012). */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_DELIVERY_OR_INFRA =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..", "..infra..")
          .as("the domain must not depend on application (delivery) or infra")
          .allowEmptyShould(true);

  /** Infra (driven adapters) must not depend on delivery (ADR 0012). */
  @ArchTest
  static final ArchRule INFRA_MUST_NOT_DEPEND_ON_DELIVERY =
      noClasses()
          .that()
          .resideInAPackage("..infra..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..application..")
          .as("infra must not depend on application (delivery)")
          .allowEmptyShould(true);

  /** Entities must not expose JavaBean setters — they mutate via business methods (ADR 0013). */
  @ArchTest
  static final ArchRule ENTITIES_MUST_NOT_EXPOSE_SETTERS =
      noMethods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Entity.class)
          .should()
          .haveNameMatching("set[A-Z].*")
          .as("@Entity classes must not expose JavaBean setters (ADR 0013)")
          .allowEmptyShould(true);

  /**
   * Entities must not use Lombok {@code @Data}/{@code @Setter} (ADR 0013). Detected by their
   * effect: a Lombok-generated method that takes parameters (setter, {@code equals}, {@code
   * canEqual}) — {@code @Getter} only generates no-arg accessors and is allowed.
   */
  @ArchTest
  static final ArchRule ENTITIES_MUST_NOT_USE_LOMBOK_DATA_OR_SETTER =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should(notExposeLombokGeneratedMutators())
          .as("@Entity classes must not use Lombok @Data or @Setter (ADR 0013)")
          .allowEmptyShould(true);

  /** No {@code *Impl} naming for internal services (CLAUDE.md). */
  @ArchTest
  static final ArchRule NO_IMPL_SUFFIX =
      noClasses()
          .should()
          .haveSimpleNameEndingWith("Impl")
          .as("no *Impl naming for internal services (CLAUDE.md)");

  /** Constructor injection only — no field {@code @Autowired} (backend.md). */
  @ArchTest
  static final ArchRule CONSTRUCTOR_INJECTION_ONLY =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .as("constructor injection only — no field @Autowired (backend.md)");

  /**
   * The external vendor DTO of the quotation-site ACL ({@code infra.integration.quotationsite})
   * must never cross into the domain (SPEC-0009 BR6): only the translated domain command leaves the
   * adapter. This proves the Anti-Corruption Layer keeps the external shape out of the model.
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_EXTERNAL_INTEGRATION_DTOS =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra.integration.quotationsite..")
          .as("the external quotation-site DTO must not cross into the domain (SPEC-0009 BR6, ACL)")
          .allowEmptyShould(true);

  private static ArchCondition<JavaClass> notExposeLombokGeneratedMutators() {
    return new ArchCondition<>("not expose Lombok-generated mutators") {
      @Override
      public void check(JavaClass entity, ConditionEvents events) {
        for (JavaMethod method : entity.getMethods()) {
          if (method.isAnnotatedWith("lombok.Generated")
              && !method.getRawParameterTypes().isEmpty()) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName() + " is a Lombok-generated mutator (@Data/@Setter)"));
          }
        }
      }
    };
  }
}
