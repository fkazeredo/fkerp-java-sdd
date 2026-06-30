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

  /**
   * Proves the "Platform orchestrates, never owns domain rules" rule (SPEC-0023 BR6) has teeth. The
   * fixture {@code archfixture.platform.CommandingPlatform} deliberately depends on another
   * module's command facade ({@code BookingService}); checking the production rule (re-pointed at
   * the fixture's source package) against it must fail.
   */
  @Test
  void platformRuleFailsWhenPlatformDependsOnACommandFacade() {
    JavaClasses fixture =
        new ClassFileImporter().importPackages("archfixture.platform", "com.fksoft.domain.booking");

    assertThatThrownBy(
            () ->
                ArchitectureTest.platformOrchestratesNeverOwnsDomainRulesForSource(
                        "archfixture.platform..")
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("platform must orchestrate, never own domain rules");
  }

  /**
   * Proves the "module-internal types are not visible across modules" rule (Phase 9 / ADR 0016 /
   * DL-0089) has teeth. After the {@code internal} sub-packages were flattened, this rule is what
   * keeps a module's {@code @ModuleInternal} implementation types hidden from other modules. The
   * fixture {@code archfixture.moduleb.ForeignConsumer} (fixture "module b") deliberately depends
   * on {@code archfixture.modulea.SecretInternal} (a {@code @ModuleInternal} type of fixture
   * "module a"); checking the production rule, re-pointed at the {@code archfixture.} root, must
   * fail.
   */
  @Test
  void moduleInternalRuleFailsWhenAnotherModuleDependsOnAModuleInternalType() {
    JavaClasses fixture =
        new ClassFileImporter().importPackages("archfixture.modulea", "archfixture.moduleb");

    assertThatThrownBy(
            () ->
                ArchitectureTest.moduleInternalNotVisibleAcrossModulesFor("archfixture.")
                    .check(fixture))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("must not be accessed from another domain module");
  }
}
