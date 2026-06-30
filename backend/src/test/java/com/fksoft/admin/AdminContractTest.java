package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.admin.AdminContractInvalidException;
import com.fksoft.domain.admin.internal.AdminContract;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link AdminContract} aggregate (SPEC-0025 BR2; DL-0087): the validity-window
 * invariant and the controlled-clock expiry-signal idempotency. Pure domain — no Spring/DB.
 */
class AdminContractTest {

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
  private static final LocalDate TODAY = NOW.atZone(ZoneOffset.UTC).toLocalDate();

  @Test
  void validUntilBeforeValidFromIsRejected() {
    assertThatThrownBy(
            () ->
                AdminContract.register(
                    UUID.randomUUID(),
                    LocalDate.of(2026, 6, 30),
                    LocalDate.of(2026, 6, 1),
                    null,
                    null,
                    null,
                    NOW,
                    "admin"))
        .isInstanceOf(AdminContractInvalidException.class);
  }

  @Test
  void missingSupplierOrValidFromIsRejected() {
    assertThatThrownBy(
            () -> AdminContract.register(null, TODAY, null, null, null, null, NOW, "admin"))
        .isInstanceOf(AdminContractInvalidException.class);
    assertThatThrownBy(
            () ->
                AdminContract.register(
                    UUID.randomUUID(), null, null, null, null, null, NOW, "admin"))
        .isInstanceOf(AdminContractInvalidException.class);
  }

  @Test
  void openEndedContractIsValidAndNeverExpiring() {
    AdminContract contract =
        AdminContract.register(UUID.randomUUID(), TODAY, null, null, null, null, NOW, "admin");
    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isFalse();
  }

  @Test
  void signalExpiringIsIdempotentWithinTheWarningWindow() {
    AdminContract contract =
        AdminContract.register(
            UUID.randomUUID(), TODAY, TODAY.plusDays(10), null, null, null, NOW, "admin");

    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isTrue();
    // Second sweep: already signaled — no re-alert.
    assertThat(contract.signalExpiringIfDue(NOW.plusSeconds(60), TODAY)).isFalse();
  }

  @Test
  void contractFarFromExpiryDoesNotSignal() {
    AdminContract contract =
        AdminContract.register(
            UUID.randomUUID(), TODAY, TODAY.plusDays(90), null, null, null, NOW, "admin");
    assertThat(contract.signalExpiringIfDue(NOW, TODAY)).isFalse();
  }
}
