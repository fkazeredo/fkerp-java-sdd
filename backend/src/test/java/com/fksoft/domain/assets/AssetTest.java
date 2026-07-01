package com.fksoft.domain.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Asset} aggregate invariants (SPEC-0021 BR1/BR3/BR4; DL-0066/DL-0068): a
 * software license requires an expiry date, registration starts ACTIVE, retirement is audited and
 * terminal, and the controlled-clock expiry signal is idempotent.
 */
class AssetTest {

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);
  private static final Money COST = Money.of(new BigDecimal("3200.00"), "BRL");

  @Test
  void registeringWithoutMandatoryDataIsRejected() {
    assertThatThrownBy(
            () ->
                Asset.register(
                    "EQUIPMENT", "  ", TODAY, COST, null, null, null, null, NOW, "admin"))
        .isInstanceOf(AssetInvalidException.class);

    assertThatThrownBy(
            () ->
                Asset.register(
                    "EQUIPMENT", "Notebook", null, COST, null, null, null, null, NOW, "admin"))
        .isInstanceOf(AssetInvalidException.class);
  }

  @Test
  void aSoftwareLicenseRequiresAnExpiryDate() {
    assertThatThrownBy(
            () ->
                Asset.register(
                    "SOFTWARE_LICENSE",
                    "JetBrains All Products Pack",
                    TODAY,
                    COST,
                    null, // no expiresAt
                    null,
                    null,
                    null,
                    NOW,
                    "admin"))
        .isInstanceOf(LicenseExpiryRequiredException.class);
  }

  @Test
  void registeringStartsActiveAndKeepsTheValueLinks() {
    Asset asset =
        Asset.register(
            "SOFTWARE_LICENSE",
            "JetBrains All Products Pack",
            TODAY,
            COST,
            LocalDate.of(2027, 1, 10),
            "JetBrains",
            null,
            null,
            NOW,
            "admin");

    assertThat(asset.status()).isEqualTo(AssetStatus.ACTIVE);
    assertThat(asset.type()).isEqualTo("SOFTWARE_LICENSE");
    assertThat(asset.expiresAt()).isEqualTo(LocalDate.of(2027, 1, 10));
    assertThat(asset.toView().acquisitionCost()).isEqualTo(COST);
  }

  @Test
  void retiringRecordsTheAuditAndIsTerminal() {
    Asset asset = equipment();

    asset.retire("Obsoleto", NOW, "admin");

    assertThat(asset.status()).isEqualTo(AssetStatus.RETIRED);
    assertThat(asset.toView().retiredBy()).isEqualTo("admin");
    assertThat(asset.toView().retirementReason()).isEqualTo("Obsoleto");

    // Terminal: retiring again is rejected, preserving the first audit (DL-0068).
    assertThatThrownBy(() -> asset.retire("again", NOW.plusSeconds(60), "other"))
        .isInstanceOf(AssetAlreadyRetiredException.class);
  }

  @Test
  void expiringLicenseSignalIsIdempotentAndScoped() {
    Asset license =
        Asset.register(
            "SOFTWARE_LICENSE",
            "Datadog",
            TODAY,
            COST,
            TODAY.plusDays(10),
            null,
            null,
            null,
            NOW,
            "admin");

    // Within the 30-day window → newly signaled once.
    assertThat(license.signalExpiringIfDue(NOW, TODAY, 30)).isTrue();
    // Idempotent: a second pass does not re-signal.
    assertThat(license.signalExpiringIfDue(NOW.plusSeconds(60), TODAY, 30)).isFalse();

    // A non-license never qualifies for the license-expiry listing.
    assertThat(equipment().isLicenseExpiringWithin(TODAY, 30)).isFalse();
  }

  private static Asset equipment() {
    return Asset.register(
        "EQUIPMENT", "Notebook Dell", TODAY, COST, null, null, null, null, NOW, "admin");
  }
}
