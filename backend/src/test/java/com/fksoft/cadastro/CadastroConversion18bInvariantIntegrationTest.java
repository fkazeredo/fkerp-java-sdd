package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.cadastro.CadastroCodeInvalidException;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.marketing.ConsentView;
import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.MarketingCodes;
import com.fksoft.domain.marketing.MarketingService;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.portfolio.GoalMetricCodes;
import com.fksoft.domain.portfolio.GoalView;
import com.fksoft.domain.portfolio.PortfolioService;
import com.fksoft.domain.portfolio.RegisterBrandCommand;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the enum→cadastro invariant for the slice-18b groups (SPEC-0031 BR4/BR5;
 * ADR-0019/DL-0116): a converted field round-trips the SAME wire value (code = old enum name), an
 * unknown/inactive code is rejected by the {@link CadastroValidator}, and the wired branching is
 * preserved (a REVENUE goal still projects the realized spread). Exercises Marketing and Portfolio
 * writes against a real Postgres (Testcontainers) with the V33+V34 seed present. The Intelligence
 * codes are system-produced (never a create payload), so their round-trip is covered by {@code
 * IntelligencePromoFxIntegrationTest}.
 */
class CadastroConversion18bInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MarketingService marketingService;
  @Autowired private PortfolioService portfolioService;
  @Autowired private CadastroValidator cadastroValidator;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM consents");
    jdbcTemplate.execute("DELETE FROM brand_realized");
    jdbcTemplate.execute("DELETE FROM brand_goals");
    jdbcTemplate.execute("DELETE FROM brand_sale_attributions");
    jdbcTemplate.execute("DELETE FROM represented_brands");
    jdbcTemplate.execute("UPDATE cadastro_item SET active = true WHERE created_by = 'system'");
  }

  @Test
  void consentPurposeAndSubjectTypeCodesRoundTripTheSameWireValue() {
    SubjectRef subject = new SubjectRef("acc-rt", MarketingCodes.ACCOUNT);
    ConsentView granted =
        marketingService.grantConsent(
            new GrantConsentCommand(
                subject, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "signup-form"),
            "agent");

    // The wire values are unchanged — the codes equal the old enum names.
    assertThat(granted.subjectType()).isEqualTo("ACCOUNT");
    assertThat(granted.purpose()).isEqualTo("NEWSLETTER");
  }

  @Test
  void anUnknownConsentPurposeCodeIsRejected() {
    SubjectRef subject = new SubjectRef("acc-bad", MarketingCodes.ACCOUNT);
    assertThatThrownBy(
            () ->
                marketingService.grantConsent(
                    new GrantConsentCommand(subject, "NOT_A_PURPOSE", LegalBasis.CONSENT, "x"),
                    "agent"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  @Test
  void anUnknownSubjectTypeCodeIsRejected() {
    SubjectRef subject = new SubjectRef("acc-bad", "NOT_A_SUBJECT");
    assertThatThrownBy(
            () ->
                marketingService.grantConsent(
                    new GrantConsentCommand(
                        subject, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "x"),
                    "agent"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  @Test
  void goalMetricCodeRoundTripsAndAnUnknownCodeIsRejected() {
    portfolioService.registerBrand(new RegisterBrandCommand("ALAMO", "Alamo"), "admin");

    GoalView goal =
        portfolioService.defineGoal(
            new com.fksoft.domain.portfolio.DefineGoalCommand(
                "ALAMO",
                "2026",
                GoalMetricCodes.REVENUE,
                Money.of(new BigDecimal("1000.00"), "BRL"),
                null),
            "admin");
    assertThat(goal.metric()).isEqualTo("REVENUE");

    assertThatThrownBy(
            () ->
                portfolioService.defineGoal(
                    new com.fksoft.domain.portfolio.DefineGoalCommand(
                        "ALAMO", "2026-05", "NOT_A_METRIC", null, 10),
                    "admin"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  @Test
  void anInactiveGoalMetricCodeIsRejected() {
    portfolioService.registerBrand(new RegisterBrandCommand("ALAMO", "Alamo"), "admin");
    // Deactivate the VOLUME goal-metric code, then try to use it → rejected.
    jdbcTemplate.update(
        "UPDATE cadastro_item SET active = false WHERE type = 'GOAL_METRIC' AND code = 'VOLUME'");
    assertThat(cadastroValidator.isValid(CadastroType.GOAL_METRIC, "VOLUME")).isFalse();

    assertThatThrownBy(
            () ->
                portfolioService.defineGoal(
                    new com.fksoft.domain.portfolio.DefineGoalCommand(
                        "ALAMO", "2026", "VOLUME", null, 10),
                    "admin"))
        .isInstanceOf(CadastroCodeInvalidException.class);
  }

  @Test
  void revenueGoalBranchingIsPreservedAcrossTheConversion() {
    portfolioService.registerBrand(new RegisterBrandCommand("ALAMO", "Alamo"), "admin");
    portfolioService.defineGoal(
        new com.fksoft.domain.portfolio.DefineGoalCommand(
            "ALAMO",
            "2026",
            GoalMetricCodes.REVENUE,
            Money.of(new BigDecimal("1000.00"), "BRL"),
            null),
        "admin");

    // Attribute a sale to the brand and record a realized spread (REVENUE branch, DL-0062).
    UUID bookingId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    portfolioService.attributeSale("ALAMO", bookingId, "admin");
    portfolioService.linkReconciliationCase(caseId, bookingId);
    portfolioService.recordSaleRevenue(
        caseId, Money.of(new BigDecimal("250.00"), "BRL"), java.time.Instant.now());

    var progress = portfolioService.goalProgress(brandId("ALAMO"), "2026");
    assertThat(progress.metric()).isEqualTo("REVENUE");
    assertThat(progress.realizedAmount().amount()).isEqualByComparingTo("250.00");
  }

  private UUID brandId(String brandRef) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM represented_brands WHERE brand_ref = ?", UUID.class, brandRef);
  }
}
