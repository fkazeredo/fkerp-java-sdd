package com.fksoft.domain.portfolio.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.portfolio.RepresentationContractInvalidException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link RepresentationContract} aggregate (SPEC-0020 BR2/BR5; DL-0063): the
 * validity window, the in-force check (DL-0061) and the controlled-clock expiry signal (idempotent,
 * within a 30-day window).
 */
class RepresentationContractTest {

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);

  private RepresentationContract contract(LocalDate from, LocalDate until) {
    return RepresentationContract.register("ALAMO", from, until, null, Map.of(), NOW, "admin");
  }

  @Test
  void registersWithAValidWindowAndTerms() {
    RepresentationContract contract =
        RepresentationContract.register(
            "ALAMO",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            Map.of("override", "15%"),
            NOW,
            "admin");
    assertThat(contract.brandRef()).isEqualTo("ALAMO");
    assertThat(contract.toView().terms()).containsEntry("override", "15%");
  }

  @Test
  void rejectsAMissingValidFrom() {
    assertThatThrownBy(() -> contract(null, LocalDate.of(2026, 12, 31)))
        .isInstanceOf(RepresentationContractInvalidException.class);
  }

  @Test
  void rejectsValidUntilBeforeValidFrom() {
    assertThatThrownBy(() -> contract(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 1)))
        .isInstanceOf(RepresentationContractInvalidException.class);
  }

  @Test
  void inForceCheckHonorsTheWindow() {
    RepresentationContract contract =
        contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    assertThat(contract.isInForceOn(LocalDate.of(2026, 6, 29))).isTrue();
    assertThat(contract.isInForceOn(LocalDate.of(2025, 12, 31))).isFalse(); // before start
    assertThat(contract.isInForceOn(LocalDate.of(2027, 1, 1))).isFalse(); // after end
  }

  @Test
  void openEndedContractIsInForceForeverAfterStart() {
    RepresentationContract contract = contract(LocalDate.of(2026, 1, 1), null);
    assertThat(contract.isInForceOn(LocalDate.of(2099, 1, 1))).isTrue();
    assertThat(contract.isInForceOn(LocalDate.of(2025, 12, 31))).isFalse();
  }

  @Test
  void signalsExpiryOnceWhenWithinTheWarningWindowAndIsIdempotent() {
    // validUntil within 30 days of TODAY → expiring.
    RepresentationContract contract = contract(LocalDate.of(2026, 1, 1), TODAY.plusDays(10));

    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isTrue();
    assertThat(contract.expiringSignaledAt()).isEqualTo(NOW);

    // Idempotent: a second sweep does not re-signal.
    assertThat(contract.signalExpiringIfDue(NOW.plusSeconds(1), TODAY)).isFalse();
  }

  @Test
  void doesNotSignalWhenStillFarFromExpiry() {
    // validUntil 60 days away → outside the 30-day window.
    RepresentationContract contract = contract(LocalDate.of(2026, 1, 1), TODAY.plusDays(60));
    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isFalse();
  }

  @Test
  void signalsAnAlreadyExpiredContractToo() {
    RepresentationContract contract = contract(LocalDate.of(2026, 1, 1), TODAY.minusDays(5));
    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isTrue();
  }

  @Test
  void neverSignalsAnOpenEndedContract() {
    RepresentationContract contract = contract(LocalDate.of(2026, 1, 1), null);
    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isFalse();
  }
}
