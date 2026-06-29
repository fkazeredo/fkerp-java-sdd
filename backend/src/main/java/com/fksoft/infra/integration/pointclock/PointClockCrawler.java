package com.fksoft.infra.integration.pointclock;

import com.fksoft.domain.people.CollectSnapshotCommand;
import com.fksoft.domain.people.PointFailureClass;
import com.fksoft.domain.people.PointSnapshotService;
import com.fksoft.domain.people.PointSnapshotView;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The point-clock crawler (SPEC-0012; redesign 7.8): the technical driving adapter (an ACL) that
 * collects the OPERATIONAL mirror from the vendor portal and drives the {@link
 * PointSnapshotService} facade to publish the snapshot. It owns the outbound resilience — the
 * project's first (DL-0031):
 *
 * <ul>
 *   <li><strong>Circuit breaker</strong>: outbound calls go through {@link
 *       PointClockCircuitBreaker}; after repeated failures the breaker opens and short-circuits, so
 *       a degraded portal cannot keep hammering the app.
 *   <li><strong>Queue / retry with dead-letter</strong>: each crawl is attempted up to {@code
 *       maxAttempts} for retryable failure classes; once attempts are exhausted (or the class is
 *       fatal, e.g. AUTHENTICATION_FAILED), the run is <strong>dead-lettered</strong> and a {@code
 *       PointCrawlingFailed} event is published. <strong>Never</strong> a fake snapshot ({@code
 *       messaging-and-integrations.md}).
 *   <li><strong>Idempotency</strong>: the snapshot is idempotent by {@code (sourceRef, periodRef)}
 *       in the People module (BR5), so a re-run does not duplicate.
 *   <li><strong>History</strong>: every attempt is recorded as a crawl-run (BR7).
 * </ul>
 *
 * <p>It never writes into core aggregates (BR6): it only calls the People facade and stores the
 * operational payload via the vault FileStorage port. The external portal shape never reaches the
 * domain — it is translated by {@link PointMirrorTranslator} (boundary enforced by ArchUnit).
 */
@Slf4j
@Component
public class PointClockCrawler {

  private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyy-MM");

  private final PointClockSource source;
  private final PointMirrorTranslator translator;
  private final PointSnapshotService pointSnapshotService;
  private final PointClockCircuitBreaker circuitBreaker;
  private final Clock clock;
  private final int maxAttempts;

  public PointClockCrawler(
      PointClockSource source,
      PointMirrorTranslator translator,
      PointSnapshotService pointSnapshotService,
      Clock clock,
      @Value("${point-clock.breaker.failure-threshold:3}") int failureThreshold,
      @Value("${point-clock.breaker.cooldown-ms:60000}") long cooldownMs,
      @Value("${point-clock.crawl.max-attempts:3}") int maxAttempts) {
    this.source = source;
    this.translator = translator;
    this.pointSnapshotService = pointSnapshotService;
    this.circuitBreaker =
        new PointClockCircuitBreaker(failureThreshold, Duration.ofMillis(cooldownMs), clock);
    this.clock = clock;
    this.maxAttempts = Math.max(1, maxAttempts);
  }

  /**
   * Crawls the current period for a source, with retry, circuit breaking and history (BR2/BR5/BR7).
   * Returns the collected snapshot on success, or {@code null} when the crawl could not produce one
   * (dead-lettered or short-circuited) — in which case a {@code PointCrawlingFailed} event has been
   * published and the run dead-lettered.
   *
   * @param sourceRef the REP/branch reference to collect
   * @return the collected snapshot view, or {@code null} on failure (never a fake snapshot)
   */
  public PointSnapshotView crawl(String sourceRef) {
    String periodRef = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(PERIOD);
    PointFailureClass lastFailure = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      String correlationId = UUID.randomUUID().toString();
      UUID runId = pointSnapshotService.startRun(sourceRef, attempt, correlationId);
      long startNanos = System.nanoTime();
      try {
        circuitBreaker.guard();
        PortalMirror mirror = source.fetchMirror(sourceRef, periodRef);
        circuitBreaker.recordSuccess();
        CollectSnapshotCommand command = translator.translate(mirror, sourceRef, periodRef);
        PointSnapshotView snapshot = pointSnapshotService.collect(command);
        pointSnapshotService.recordRunSucceeded(runId, periodRef, command.marks());
        log.info(
            "PointCrawl succeeded sourceRef={} periodRef={} attempt={} marks={} latencyMs={} correlationId={}",
            sourceRef,
            periodRef,
            attempt,
            command.marks(),
            millisSince(startNanos),
            correlationId);
        return snapshot;
      } catch (PointClockCircuitBreaker.CircuitOpenException open) {
        // Short-circuited: the breaker is open. Dead-letter this run without hitting the portal.
        lastFailure = PointFailureClass.UNAVAILABLE;
        pointSnapshotService.recordRunFailed(runId, sourceRef, lastFailure, true);
        log.warn(
            "PointCrawl short-circuited sourceRef={} attempt={} correlationId={} (breaker OPEN)",
            sourceRef,
            attempt,
            correlationId);
        return null;
      } catch (PointClockSourceException failure) {
        lastFailure = failure.failureClass();
        circuitBreaker.recordFailure();
        boolean lastAttempt = attempt >= maxAttempts;
        boolean retryable = lastFailure.retryable() && !lastAttempt;
        boolean deadLettered = !retryable;
        pointSnapshotService.recordRunFailed(runId, sourceRef, lastFailure, deadLettered);
        log.warn(
            "PointCrawl failed sourceRef={} periodRef={} attempt={} failureClass={} retryable={} latencyMs={} correlationId={}",
            sourceRef,
            periodRef,
            attempt,
            lastFailure,
            retryable,
            millisSince(startNanos),
            correlationId);
        if (deadLettered) {
          return null;
        }
        // else: loop to the next attempt (the failure class is retryable).
      }
    }
    log.warn("PointCrawl exhausted attempts sourceRef={} lastFailure={}", sourceRef, lastFailure);
    return null;
  }

  /** The current circuit-breaker state (observability/health). */
  public PointClockCircuitBreaker.State breakerState() {
    return circuitBreaker.state();
  }

  private static long millisSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
