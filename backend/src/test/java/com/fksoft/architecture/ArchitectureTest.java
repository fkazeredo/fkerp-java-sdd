package com.fksoft.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.fksoft.domain.ModuleInternal;
import com.tngtech.archunit.core.domain.Dependency;
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

  /**
   * Every REST controller MUST carry an {@code @Tag} so the generated OpenAPI/Swagger UI groups it
   * (SPEC-0024 Fase 19d, DL-0126). This is the completeness fitness function for the API docs: a
   * new controller without a tag fails the build, so the docs cannot silently drift from the API.
   */
  @ArchTest
  static final ArchRule REST_CONTROLLERS_ARE_TAGGED_FOR_OPENAPI =
      classes()
          .that()
          .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
          .should()
          .beAnnotatedWith(io.swagger.v3.oas.annotations.tags.Tag.class)
          .as("every @RestController must carry an @Tag for the OpenAPI docs (DL-0126)");

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

  /**
   * Observability is an infra concern (SPEC-0027/DL-0098): the pure {@code domain} must not depend
   * on Micrometer or Spring Boot Actuator. Business metrics are instrumented at the infra boundary
   * (the {@link com.fksoft.infra.observability.BusinessMetrics} event consumer), so a counter never
   * leaks into a domain service. Planting an {@code io.micrometer} import in a domain class makes
   * this fail.
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_METRICS_OR_ACTUATOR =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("io.micrometer..", "org.springframework.boot.actuate..")
          .as(
              "the domain must not depend on Micrometer or Actuator (observability is infra — DL-0098)")
          .allowEmptyShould(true);

  /**
   * Module encapsulation after the Phase 9 flatten (ADR 0016 / DL-0089). The {@code internal}
   * sub-packages were removed, so Spring Modulith no longer auto-hides a module's implementation
   * types — the boundary now lives on the {@link ModuleInternal} type marker. This rule re-creates,
   * for all domain modules, exactly what the {@code internal} sub-package used to grant Modulith: a
   * type marked {@code @ModuleInternal} (a module's implementation) must never be depended upon by
   * a class belonging to <em>another</em> domain module. The owning module itself (including its
   * tests, which share the {@code …<module>} package segment) and the {@code infra} layer (which
   * may operate a module's persistence — ADR 0010/0012) are exempt. Planting a cross-module
   * dependency on a {@code @ModuleInternal} type makes this fail (see {@link
   * ArchitectureRulesHaveTeethTest}).
   */
  @ArchTest
  static final ArchRule MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES =
      moduleInternalNotVisibleAcrossModules();

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

  /**
   * The external vendor shape of the point-clock crawler ({@code infra.integration.pointclock} —
   * the {@link com.fksoft.infra.integration.pointclock.PortalMirror} and the rest of the adapter)
   * must never cross into the domain (SPEC-0012 BR6, ACL/DL-0030): only the translated {@link
   * com.fksoft.domain.people.CollectSnapshotCommand} leaves the adapter. This proves the crawler's
   * Anti-Corruption Layer keeps the portal's shape out of the model.
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_POINT_CLOCK_ADAPTER =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra.integration.pointclock..")
          .as(
              "the external point-clock portal shape must not cross into the domain (SPEC-0012 BR6, ACL)")
          .allowEmptyShould(true);

  /**
   * The external vendor shape of the municipal NFS-e webservice ({@code infra.integration.nfse} —
   * the {@link com.fksoft.infra.integration.nfse.MunicipalNfseEnvelope} and the rest of the
   * adapter) must never cross into the domain (SPEC-0016 BR3, ACL/DL-0046): only the translated
   * domain types ({@code NfseIssuance}/{@code NfseIssueRequest}) leave the adapter. This proves the
   * NFS-e Anti-Corruption Layer keeps the municipality's shape out of the model.
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_NFSE_ADAPTER =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra.integration.nfse..")
          .as(
              "the external municipal NFS-e shape must not cross into the domain (SPEC-0016 BR3, ACL)")
          .allowEmptyShould(true);

  /**
   * The external vendor shape of the payment provider ({@code infra.integration.payment} — the
   * {@link com.fksoft.infra.integration.payment.PaymentWebhookPayload} and the rest of the adapter)
   * must never cross into the domain (SPEC-0017 Scope, ACL/DL-0048): only the domain port types
   * ({@code PaymentInstruction}/{@code PaymentRequestResult}) and outcomes leave the adapter. This
   * proves the payment Anti-Corruption Layer keeps the provider's shape out of the model (ADR
   * 0006).
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_PAYMENT_ADAPTER =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra.integration.payment..")
          .as("the external payment-provider shape must not cross into the domain (SPEC-0017, ACL)")
          .allowEmptyShould(true);

  /**
   * The external vendor shape of the newsletter provider ({@code infra.integration.newsletter} —
   * the {@link com.fksoft.infra.integration.newsletter.NewsletterProviderRequest}/{@code
   * NewsletterProviderResponse} and the rest of the adapter) must never cross into the domain
   * (SPEC-0019 Scope, ACL/DL-0055): only the domain port types ({@code NewsletterMessage}/{@code
   * NewsletterSendResult}) leave the adapter. This proves the newsletter Anti-Corruption Layer
   * keeps the provider's shape out of the model (ADR 0007).
   */
  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_NEWSLETTER_ADAPTER =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infra.integration.newsletter..")
          .as(
              "the external newsletter-provider shape must not cross into the domain (SPEC-0019, ACL)")
          .allowEmptyShould(true);

  /**
   * The point-clock crawler MUST NOT write into the core (SPEC-0012 BR6): it communicates only via
   * the People facade and in-process events. This rule proves it never touches any core business
   * module — directly or through their internals. It may use {@code people} (the operational
   * facade) and {@code compliance} (the vault, for the operational payload store and the AFD path),
   * {@code money} and the error kernel — but not
   * quoting/booking/exchange/reconciliation/commissioning/ finance/accounts/sourcing. Planting a
   * dependency on, say, {@code domain.booking} makes this fail.
   */
  @ArchTest
  static final ArchRule POINT_CLOCK_CRAWLER_MUST_NOT_WRITE_INTO_THE_CORE =
      noClasses()
          .that()
          .resideInAPackage("..infra.integration.pointclock..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..domain.quoting..",
              "..domain.booking..",
              "..domain.exchange..",
              "..domain.reconciliation..",
              "..domain.commissioning..",
              "..domain.finance..",
              "..domain.accounts..",
              "..domain.sourcing..")
          .as("the point-clock crawler must not write into the core (SPEC-0012 BR6)")
          .allowEmptyShould(true);

  /**
   * The Intelligence (DSS) module ADVISES, it never COMMANDS (SPEC-0013 BR2, redesign Part 8): it
   * consumes other modules' events and reads projections, but it MUST NOT invoke any other module's
   * command facade. This rule proves the principle structurally — {@code intelligence} must not
   * depend on any other domain module's {@code *Service} (the command facades) nor reach into any
   * other module's {@code internal} package. It may still depend on the exposed events/views/value
   * objects, the {@code money} kernel and the error kernel. Planting a dependency on, say, {@code
   * BookingService} or {@code exchange.internal} makes this fail (see {@link
   * ArchitectureRulesHaveTeethTest}).
   */
  @ArchTest
  static final ArchRule INTELLIGENCE_ADVISES_NEVER_COMMANDS =
      intelligenceAdvisesNeverCommandsForSource("..domain.intelligence..");

  /**
   * The Portfolio module REFERENCES brands and PROJECTS the realized from sales events; it MUST NOT
   * price nor command the sale (SPEC-0020 BR6/DL-0061/DL-0062). This rule proves the principle
   * structurally — {@code portfolio} must not depend on any other domain module's {@code *Service}
   * (the command facades, e.g. {@code BookingService}/{@code ReconciliationService}) nor reach into
   * any other module's {@code internal} package. It may still depend on the exposed events/views/
   * value objects, the {@code money} kernel and the error kernel. Planting a dependency on, say,
   * {@code BookingService} makes this fail.
   */
  @ArchTest
  static final ArchRule PORTFOLIO_REFERENCES_NEVER_COMMANDS_THE_SALE =
      noClasses()
          .that()
          .resideInAPackage("..domain.portfolio..")
          .should()
          .dependOnClassesThat(isForeignCommandFacadeOrInternal("com.fksoft.domain.portfolio"))
          .as(
              "portfolio must reference, never command the sale: no dependency on another module's "
                  + "*Service or internal package (SPEC-0020 BR6)")
          .allowEmptyShould(true);

  /**
   * The Platform module is operated INFRA — it orchestrates, guards and audits, but it MUST NOT
   * contain business-domain rules nor reach into any other business module (SPEC-0023 BR6). This
   * rule proves the principle structurally: {@code platform} must not depend on any other domain
   * module's {@code *Service} (the command facades) nor reach into any other module's {@code
   * internal} package. It may still depend on the exposed events/views/value objects (it consumes
   * other modules' events for the audit), the {@code money} kernel and the error kernel. Planting a
   * dependency on, say, {@code BookingService} or {@code billing.internal} makes this fail.
   */
  @ArchTest
  static final ArchRule PLATFORM_ORCHESTRATES_NEVER_OWNS_DOMAIN_RULES =
      platformOrchestratesNeverOwnsDomainRulesForSource("..domain.platform..");

  /**
   * Builds the "Platform orchestrates, never owns domain rules" rule for a given source package.
   * Production uses {@code ..domain.platform..}; the teeth test re-points it at the fixture package
   * to prove the rule actually fails when a platform-side type touches a command facade.
   */
  static ArchRule platformOrchestratesNeverOwnsDomainRulesForSource(String sourcePackage) {
    return noClasses()
        .that()
        .resideInAPackage(sourcePackage)
        .should()
        .dependOnClassesThat(isForeignCommandFacadeOrInternal("com.fksoft.domain.platform"))
        .as(
            "platform must orchestrate, never own domain rules: no dependency on another module's "
                + "*Service or internal package (SPEC-0023 BR6)")
        .allowEmptyShould(true);
  }

  /**
   * Predicate: a target type that is another domain module's {@code *Service} command facade or
   * sits in another module's {@code internal} package (relative to {@code ownModulePrefix}).
   */
  private static com.tngtech.archunit.base.DescribedPredicate<JavaClass>
      isForeignCommandFacadeOrInternal(String ownModulePrefix) {
    return new com.tngtech.archunit.base.DescribedPredicate<>(
        "another domain module's *Service command facade or internal package") {
      @Override
      public boolean test(JavaClass target) {
        String pkg = target.getPackageName();
        if (!pkg.startsWith("com.fksoft.domain.") || pkg.startsWith(ownModulePrefix)) {
          return false;
        }
        boolean otherInternal =
            pkg.contains(".internal") || target.isAnnotatedWith(ModuleInternal.class);
        boolean commandFacade = target.getSimpleName().endsWith("Service");
        return otherInternal || commandFacade;
      }
    };
  }

  /**
   * Builds the "advises, never commands" rule for a given source package. Production uses {@code
   * ..domain.intelligence..}; the teeth test re-points it at the fixture package to prove the rule
   * actually fails when an intelligence-side type touches a command facade.
   */
  static ArchRule intelligenceAdvisesNeverCommandsForSource(String sourcePackage) {
    return noClasses()
        .that()
        .resideInAPackage(sourcePackage)
        .should()
        .dependOnClassesThat(isAnotherModuleCommandFacadeOrInternal())
        .as(
            "intelligence must advise, never command: no dependency on another module's "
                + "*Service or internal package (SPEC-0013 BR2)")
        .allowEmptyShould(true);
  }

  private static com.tngtech.archunit.base.DescribedPredicate<JavaClass>
      isAnotherModuleCommandFacadeOrInternal() {
    return new com.tngtech.archunit.base.DescribedPredicate<>(
        "another domain module's *Service command facade or internal package") {
      @Override
      public boolean test(JavaClass target) {
        String pkg = target.getPackageName();
        if (!pkg.startsWith("com.fksoft.domain.")
            || pkg.startsWith("com.fksoft.domain.intelligence")) {
          return false;
        }
        boolean otherInternal =
            pkg.contains(".internal") || target.isAnnotatedWith(ModuleInternal.class);
        boolean commandFacade = target.getSimpleName().endsWith("Service");
        return otherInternal || commandFacade;
      }
    };
  }

  /**
   * Builds the "module-internal types are not visible across modules" rule. A type annotated {@link
   * ModuleInternal} (a module's implementation, formerly its {@code internal} sub-package) must not
   * be depended upon by a class belonging to another domain module. Origins outside {@code
   * com.fksoft.domain} (the {@code application} and {@code infra} layers) are governed by their own
   * layer rules and are not flagged here — in particular {@code infra} may operate a module's
   * persistence (ADR 0010/0012). Test classes are excluded from analysis by the {@code
   * DoNotIncludeTests} import option, so a module's own tests reaching its internals never trip it.
   */
  static ArchRule moduleInternalNotVisibleAcrossModules() {
    return moduleInternalNotVisibleAcrossModulesFor("com.fksoft.domain.");
  }

  /**
   * Builds the rule for a given domain root prefix. Production uses {@code com.fksoft.domain.}; the
   * teeth test re-points it at the fixture root ({@code archfixture.}) to prove the rule actually
   * fails when a class of one fixture module depends on a {@code @ModuleInternal} type of another.
   */
  static ArchRule moduleInternalNotVisibleAcrossModulesFor(String domainRootPrefix) {
    return classes()
        .that()
        .areAnnotatedWith(ModuleInternal.class)
        .should(notBeAccessedFromAnotherDomainModule(domainRootPrefix))
        .as(
            "a @ModuleInternal type must not be accessed from another domain module "
                + "(Phase 9 / ADR 0016 — encapsulation moved from the internal sub-package to the marker)")
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> notBeAccessedFromAnotherDomainModule(
      String domainRootPrefix) {
    return new ArchCondition<>("not be accessed from another domain module") {
      @Override
      public void check(JavaClass target, ConditionEvents events) {
        String targetModule = domainModuleOf(target.getPackageName(), domainRootPrefix);
        if (targetModule == null) {
          return; // marker misapplied outside a domain module — nothing to enforce here
        }
        for (Dependency dependency : target.getDirectDependenciesToSelf()) {
          JavaClass origin = dependency.getOriginClass();
          String originModule = domainModuleOf(origin.getPackageName(), domainRootPrefix);
          // Only flag origins that are themselves in a domain module other than the target's.
          // application/infra origins (originModule == null) are out of scope (own layer rules).
          if (originModule != null && !originModule.equals(targetModule)) {
            events.add(
                SimpleConditionEvent.violated(
                    target,
                    origin.getName()
                        + " (module "
                        + originModule
                        + ") depends on @ModuleInternal "
                        + target.getName()
                        + " of module "
                        + targetModule));
          }
        }
      }
    };
  }

  /**
   * The domain-module segment of a package under {@code domainRootPrefix}, or {@code null} if the
   * package is not under it (e.g. the {@code application}/{@code infra} layers, or the {@code
   * domain.error}/{@code domain.money}/{@code domain} kernel itself, which have no module segment).
   */
  private static String domainModuleOf(String packageName, String domainRootPrefix) {
    if (!packageName.startsWith(domainRootPrefix)) {
      return null;
    }
    String rest = packageName.substring(domainRootPrefix.length());
    if (rest.isEmpty()) {
      return null;
    }
    int dot = rest.indexOf('.');
    String segment = dot < 0 ? rest : rest.substring(0, dot);
    // error/money are non-module kernels, not business modules.
    if (segment.equals("error") || segment.equals("money")) {
      return null;
    }
    return segment;
  }

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
