package com.fksoft.domain.aftersales;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Timezone hardening for the SLA window (SPEC-0018 BR4, Fase 19i/DL-0131): the 72h resolution
 * window is exact instant arithmetic — the breach flips at deadline+1s and not one second earlier,
 * regardless of the JVM default timezone. Guards a regression to calendar-based (LocalDateTime)
 * deadline math, which would drift when the default zone crosses a day boundary.
 */
class SupportCaseSlaWindowTest {

  private final TimeZone originalZone = TimeZone.getDefault();

  @AfterEach
  void restoreZone() {
    TimeZone.setDefault(originalZone);
  }

  @Test
  void the72hWindowIsExactToTheSecondUnderAShiftedDefaultZone() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    // 02:00Z = 23:00 of the previous day in São Paulo — the calendar day disagrees with UTC.
    Instant openedAt = Instant.parse("2031-03-05T02:00:00Z");
    Instant dueAt = openedAt.plus(Duration.ofHours(72));
    SupportCase supportCase =
        SupportCase.open("bk-tz", "COMPLAINT", null, dueAt, dueAt, openedAt, "tester");

    assertThat(supportCase.markBreachedIfDue(dueAt)).isFalse();
    assertThat(supportCase.markBreachedIfDue(dueAt.plusSeconds(1))).isTrue();
  }

  @Test
  void theWindowDoesNotDriftWhenTheDefaultZoneIsEastOfUtc() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
    Instant openedAt = Instant.parse("2031-03-31T20:00:00Z"); // already April 1st in Tokyo
    Instant dueAt = openedAt.plus(Duration.ofHours(72));
    SupportCase supportCase =
        SupportCase.open("bk-tz", "COMPLAINT", null, dueAt, dueAt, openedAt, "tester");

    assertThat(supportCase.markBreachedIfDue(dueAt.minusSeconds(1))).isFalse();
    assertThat(supportCase.markBreachedIfDue(dueAt.plusSeconds(1))).isTrue();
  }
}
