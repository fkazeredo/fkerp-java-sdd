package com.fksoft.infra.jobs;

import com.fksoft.domain.booking.BookingService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the booking module to expire stale PENDING
 * bookings (SPEC-0006 BR4). The business rule (which bookings, what reason, what event) lives in
 * the domain {@link BookingService}; this adapter only supplies the schedule and the cutoff.
 * Idempotent: a booking already cancelled is no longer PENDING, so re-runs are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTimeoutScheduler {

  private final BookingService bookingService;
  private final Clock clock;

  /** Sweeps PENDING bookings older than the 72h timeout. Interval is configurable for ops/tests. */
  @Scheduled(
      initialDelayString = "${booking.timeout.initial-delay-ms:600000}",
      fixedDelayString = "${booking.timeout.sweep-interval-ms:3600000}")
  public void sweepExpiredPendingBookings() {
    int expired =
        bookingService.expirePendingBookings(clock.instant().minus(BookingService.PENDING_TIMEOUT));
    if (expired > 0) {
      log.info("PENDING-timeout sweep cancelled {} booking(s)", expired);
    }
  }
}
