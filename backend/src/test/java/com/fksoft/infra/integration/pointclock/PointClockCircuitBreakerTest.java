package com.fksoft.infra.integration.pointclock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the point-clock circuit breaker (SPEC-0012; DL-0031), driven by a controlled clock
 * so it is deterministic without sleeping. Proves: it opens after N consecutive failures and
 * short-circuits; it stays open during the cooldown; after the cooldown it half-opens and a success
 * closes it; a failure while half-open re-opens it.
 */
class PointClockCircuitBreakerTest {

  /** A controlled clock whose instant can be advanced by the test. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @Test
  void opensAfterThresholdConsecutiveFailuresAndShortCircuits() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-26T03:00:00Z"));
    PointClockCircuitBreaker breaker =
        new PointClockCircuitBreaker(3, Duration.ofSeconds(60), clock);

    assertThat(breaker.allowRequest()).isTrue();
    breaker.recordFailure();
    breaker.recordFailure();
    assertThat(breaker.state()).isEqualTo(PointClockCircuitBreaker.State.CLOSED);
    breaker.recordFailure(); // 3rd consecutive → trips OPEN

    assertThat(breaker.state()).isEqualTo(PointClockCircuitBreaker.State.OPEN);
    assertThat(breaker.allowRequest()).isFalse(); // short-circuited within cooldown
  }

  @Test
  void halfOpensAfterCooldownAndClosesOnSuccess() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-26T03:00:00Z"));
    PointClockCircuitBreaker breaker =
        new PointClockCircuitBreaker(2, Duration.ofSeconds(60), clock);
    breaker.recordFailure();
    breaker.recordFailure(); // OPEN
    assertThat(breaker.allowRequest()).isFalse();

    clock.advance(Duration.ofSeconds(61)); // cooldown elapsed

    assertThat(breaker.allowRequest()).isTrue(); // HALF_OPEN trial allowed
    assertThat(breaker.state()).isEqualTo(PointClockCircuitBreaker.State.HALF_OPEN);
    breaker.recordSuccess();
    assertThat(breaker.state()).isEqualTo(PointClockCircuitBreaker.State.CLOSED);
  }

  @Test
  void reOpensWhenTheHalfOpenTrialFails() {
    MutableClock clock = new MutableClock(Instant.parse("2026-06-26T03:00:00Z"));
    PointClockCircuitBreaker breaker =
        new PointClockCircuitBreaker(2, Duration.ofSeconds(60), clock);
    breaker.recordFailure();
    breaker.recordFailure(); // OPEN
    clock.advance(Duration.ofSeconds(61));
    breaker.allowRequest(); // → HALF_OPEN

    breaker.recordFailure(); // trial failed → back to OPEN

    assertThat(breaker.state()).isEqualTo(PointClockCircuitBreaker.State.OPEN);
    assertThat(breaker.allowRequest()).isFalse();
  }
}
