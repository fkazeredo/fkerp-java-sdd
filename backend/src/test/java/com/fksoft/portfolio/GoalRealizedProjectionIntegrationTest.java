package com.fksoft.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.booking.BookingConfirmed;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.portfolio.BrandGoalInvalidException;
import com.fksoft.domain.portfolio.DefineGoalCommand;
import com.fksoft.domain.portfolio.GoalMetric;
import com.fksoft.domain.portfolio.GoalProgress;
import com.fksoft.domain.portfolio.GoalView;
import com.fksoft.domain.portfolio.PortfolioService;
import com.fksoft.domain.portfolio.RegisterBrandCommand;
import com.fksoft.domain.reconciliation.ReconciliationCaseOpened;
import com.fksoft.domain.reconciliation.SpreadRealized;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for slice 8g-2 (SPEC-0020 BR3/BR4/BR6; V25; DL-0062): defining goals
 * (VOLUME/REVENUE, unique per (brand, period, metric)) and the <strong>realized-vs-goal
 * projection</strong> built from real sales events — {@code BookingConfirmed} (VOLUME) and {@code
 * SpreadRealized} (REVENUE, BRL) matched to the brand by the Portfolio-owned sale-attribution
 * intake — proving the projection is idempotent and that an event with no attributed brand
 * contributes nothing. Drives {@link PortfolioService} and publishes the producing modules' events
 * through the {@link ApplicationEventPublisher} (the Portfolio listeners react), against a real
 * Postgres.
 */
class GoalRealizedProjectionIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PortfolioService portfolioService;
  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final Instant IN_2026 = Instant.parse("2026-03-15T10:00:00Z");

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM brand_realized");
    jdbcTemplate.execute("DELETE FROM brand_sale_attributions");
    jdbcTemplate.execute("DELETE FROM brand_goals");
    jdbcTemplate.execute("DELETE FROM represented_brands");
  }

  private void registerAlamo() {
    portfolioService.registerBrand(new RegisterBrandCommand("ALAMO", "Alamo Rent a Car"), "admin");
  }

  @Test
  void definingAGoalIsUniquePerBrandPeriodMetric() {
    registerAlamo();
    GoalView goal =
        portfolioService.defineGoal(
            new DefineGoalCommand(
                "ALAMO",
                "2026",
                GoalMetric.REVENUE,
                Money.of(new BigDecimal("1200000.00"), "BRL"),
                null),
            "admin");
    assertThat(goal.metric()).isEqualTo(GoalMetric.REVENUE);

    // A second goal for the same (brand, period, metric) is rejected.
    assertThatThrownBy(
            () ->
                portfolioService.defineGoal(
                    new DefineGoalCommand(
                        "ALAMO",
                        "2026",
                        GoalMetric.REVENUE,
                        Money.of(new BigDecimal("999.00"), "BRL"),
                        null),
                    "admin"))
        .isInstanceOf(BrandGoalInvalidException.class);
  }

  @Test
  void confirmedSalesOfTheBrandIncrementTheVolumeRealized() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand("ALAMO", "2026", GoalMetric.VOLUME, null, 10), "admin");

    // Two bookings attributed to ALAMO, then confirmed → VOLUME realized = 2.
    UUID b1 = UUID.randomUUID();
    UUID b2 = UUID.randomUUID();
    UUID bOther = UUID.randomUUID(); // not attributed → must not count
    portfolioService.attributeSale("ALAMO", b1, "agent");
    portfolioService.attributeSale("ALAMO", b2, "agent");

    publisher.publishEvent(new BookingConfirmed(b1, UUID.randomUUID(), UUID.randomUUID(), IN_2026));
    publisher.publishEvent(new BookingConfirmed(b2, UUID.randomUUID(), UUID.randomUUID(), IN_2026));
    publisher.publishEvent(
        new BookingConfirmed(bOther, UUID.randomUUID(), UUID.randomUUID(), IN_2026));

    GoalProgress progress = portfolioService.goalProgress(brandId, "2026");
    assertThat(progress.metric()).isEqualTo(GoalMetric.VOLUME);
    assertThat(progress.realizedCount()).isEqualTo(2);
    assertThat(progress.targetCount()).isEqualTo(10);
    assertThat(progress.attainmentPct()).isEqualByComparingTo("20.0");
  }

  @Test
  void volumeProjectionIsIdempotentPerBooking() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand("ALAMO", "2026", GoalMetric.VOLUME, null, 10), "admin");

    UUID booking = UUID.randomUUID();
    portfolioService.attributeSale("ALAMO", booking, "agent");

    BookingConfirmed event =
        new BookingConfirmed(booking, UUID.randomUUID(), UUID.randomUUID(), IN_2026);
    publisher.publishEvent(event);
    publisher.publishEvent(event); // re-delivered → must not double-count

    assertThat(portfolioService.goalProgress(brandId, "2026").realizedCount()).isEqualTo(1);
  }

  @Test
  void realizedSpreadOfTheBrandAccumulatesTheRevenueRealized() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand(
            "ALAMO",
            "2026",
            GoalMetric.REVENUE,
            Money.of(new BigDecimal("1200000.00"), "BRL"),
            null),
        "admin");

    UUID booking = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    portfolioService.attributeSale("ALAMO", booking, "agent");
    // The reconciliation case links to the booking (so SpreadRealized's caseId resolves to ALAMO).
    publisher.publishEvent(new ReconciliationCaseOpened(caseId, booking, IN_2026));
    publisher.publishEvent(
        new SpreadRealized(
            caseId, Money.of(new BigDecimal("480000.00"), "BRL"), Money.zero("BRL"), IN_2026));

    GoalProgress progress = portfolioService.goalProgress(brandId, "2026");
    assertThat(progress.metric()).isEqualTo(GoalMetric.REVENUE);
    assertThat(progress.realizedAmount()).isEqualTo(Money.of(new BigDecimal("480000.00"), "BRL"));
    assertThat(progress.attainmentPct()).isEqualByComparingTo("40.0"); // 480k / 1.2M
  }

  @Test
  void revenueProjectionIsIdempotentPerCase() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand(
            "ALAMO", "2026", GoalMetric.REVENUE, Money.of(new BigDecimal("1000.00"), "BRL"), null),
        "admin");

    UUID booking = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    portfolioService.attributeSale("ALAMO", booking, "agent");
    publisher.publishEvent(new ReconciliationCaseOpened(caseId, booking, IN_2026));

    SpreadRealized spread =
        new SpreadRealized(
            caseId, Money.of(new BigDecimal("400.00"), "BRL"), Money.zero("BRL"), IN_2026);
    publisher.publishEvent(spread);
    publisher.publishEvent(spread); // re-delivered → must not double-count

    assertThat(portfolioService.goalProgress(brandId, "2026").realizedAmount())
        .isEqualTo(Money.of(new BigDecimal("400.00"), "BRL"));
  }

  @Test
  void aSaleWithNoAttributedBrandContributesNothing() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand("ALAMO", "2026", GoalMetric.VOLUME, null, 10), "admin");

    // A confirmed booking that was never attributed to a brand must not move any goal.
    publisher.publishEvent(
        new BookingConfirmed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), IN_2026));

    assertThat(portfolioService.goalProgress(brandId, "2026").realizedCount()).isZero();
  }

  @Test
  void salesOutsideThePeriodDoNotCount() {
    registerAlamo();
    UUID brandId = portfolioService.listBrands(null).get(0).id();
    portfolioService.defineGoal(
        new DefineGoalCommand("ALAMO", "2026", GoalMetric.VOLUME, null, 10), "admin");

    UUID booking = UUID.randomUUID();
    portfolioService.attributeSale("ALAMO", booking, "agent");
    // Confirmed in 2025 → outside the 2026 goal period.
    publisher.publishEvent(
        new BookingConfirmed(
            booking, UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2025-12-31T23:00:00Z")));

    assertThat(portfolioService.goalProgress(brandId, "2026").realizedCount()).isZero();
  }

  @Test
  void attributeSaleIsIdempotentPerBooking() {
    registerAlamo();
    UUID booking = UUID.randomUUID();
    assertThat(portfolioService.attributeSale("ALAMO", booking, "agent")).isEqualTo("ALAMO");
    // Re-registering the same booking returns the existing link (no duplicate, no error).
    assertThat(portfolioService.attributeSale("ALAMO", booking, "agent")).isEqualTo("ALAMO");

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM brand_sale_attributions WHERE booking_id = ?",
            Integer.class,
            booking);
    assertThat(rows).isEqualTo(1);
  }
}
