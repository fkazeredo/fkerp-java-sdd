package com.fksoft.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.assets.AssetLicenseExpiring;
import com.fksoft.domain.assets.AssetRegistered;
import com.fksoft.domain.assets.AssetService;
import com.fksoft.domain.assets.AssetView;
import com.fksoft.domain.assets.RegisterAssetCommand;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.jobs.AssetLicenseExpiryScheduler;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for slice 8h-2 (SPEC-0021 BR3; DL-0066): the controlled-clock license-expiry
 * alert publishes {@code AssetLicenseExpiring} once per due license (idempotent), ignores licenses
 * far from expiry, and the ad-hoc {@code ?expiringWithinDays} listing returns the licenses expiring
 * within the window (and only active software licenses). Also asserts the {@link
 * AssetLicenseExpiryScheduler} adapter is wired. Runs against a real Postgres (Testcontainers).
 */
@RecordApplicationEvents
class AssetLicenseExpiryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AssetService assetService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private AssetLicenseExpiryScheduler scheduler;

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
  private static final LocalDate TODAY = NOW.atZone(ZoneOffset.UTC).toLocalDate();
  private static final Money COST = Money.of(new BigDecimal("3200.00"), "BRL");

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM assets");
  }

  private AssetView license(String identifier, LocalDate expiresAt) {
    return assetService.register(
        new RegisterAssetCommand(
            "SOFTWARE_LICENSE", identifier, TODAY, COST, expiresAt, null, null, null),
        "admin");
  }

  @Test
  void theSchedulerAdapterIsWired() {
    assertThat(scheduler).isNotNull();
  }

  @Test
  void sweepPublishesAssetLicenseExpiringOnceForADueLicense() {
    AssetView jetbrains = license("JetBrains", TODAY.plusDays(10)); // within the 30-day window

    int flagged = assetService.flagExpiringLicenses(NOW);
    assertThat(flagged).isEqualTo(1);
    assertThat(
            applicationEvents.stream(AssetLicenseExpiring.class)
                .filter(e -> e.assetId().equals(jetbrains.id()))
                .count())
        .isEqualTo(1);

    // Idempotent: a second sweep does not re-publish.
    assertThat(assetService.flagExpiringLicenses(NOW.plusSeconds(60))).isZero();
  }

  @Test
  void sweepIgnoresLicensesFarFromExpiryAndNonLicenses() {
    license("Datadog", TODAY.plusDays(90)); // far from expiry
    // A non-license never participates in the license-expiry sweep.
    assetService.register(
        new RegisterAssetCommand(
            "EQUIPMENT", "Notebook", TODAY, COST, null, null, null, null),
        "admin");

    assertThat(assetService.flagExpiringLicenses(NOW)).isZero();
  }

  @Test
  void expiringWithinDaysListsOnlyDueActiveLicenses() {
    AssetView due = license("Due", TODAY.plusDays(15));
    license("Far", TODAY.plusDays(120));

    assertThat(assetService.list(null, null, 30))
        .extracting(AssetView::id)
        .containsExactly(due.id());
  }

  @Test
  void registeringPublishesAssetRegistered() {
    AssetView created = license("Sentry", TODAY.plusDays(40));
    assertThat(
            applicationEvents.stream(AssetRegistered.class)
                .filter(e -> e.assetId().equals(created.id()))
                .count())
        .isEqualTo(1);
  }
}
