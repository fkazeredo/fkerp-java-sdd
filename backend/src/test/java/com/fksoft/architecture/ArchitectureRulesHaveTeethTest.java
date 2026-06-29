package com.fksoft.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Proves the layer rule has teeth (SPEC-0001: "um teste deve falhar ao plantar uma violação de
 * fronteira"). The fixture package {@code archfixture} deliberately plants a {@code domain ->
 * infra} dependency; reusing the production rule against it must fail. The fixture lives outside
 * {@code com.fksoft}, so the production {@link ArchitectureTest} never sees it.
 */
class ArchitectureRulesHaveTeethTest {

  @Test
  void domainRuleFailsWhenDomainDependsOnInfra() {
    JavaClasses fixture = new ClassFileImporter().importPackages("archfixture");

    assertThatThrownBy(
            () -> ArchitectureTest.DOMAIN_MUST_NOT_DEPEND_ON_DELIVERY_OR_INFRA.check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("the domain must not depend on application (delivery) or infra");
  }

  /**
   * Proves the "advises, never commands" rule (SPEC-0013 BR2) has teeth. The fixture {@code
   * archfixture.intelligence.CommandingInsight} deliberately depends on another module's command
   * facade ({@code BookingService}); checking the production rule (re-pointed at the fixture's
   * source package) against it must fail. Importing the production booking package too makes the
   * dependency resolvable.
   */
  @Test
  void intelligenceRuleFailsWhenIntelligenceDependsOnACommandFacade() {
    JavaClasses fixture =
        new ClassFileImporter()
            .importPackages("archfixture.intelligence", "com.fksoft.domain.booking");

    assertThatThrownBy(
            () ->
                ArchitectureTest.intelligenceAdvisesNeverCommandsForSource(
                        "archfixture.intelligence..")
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("intelligence must advise, never command");
  }
}
