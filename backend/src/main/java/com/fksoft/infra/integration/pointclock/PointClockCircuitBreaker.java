package com.fksoft.infra.integration.pointclock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * A small, deterministic circuit breaker for the point-clock outbound call (SPEC-0012; DL-0031;
 * {@code messaging-and-integrations.md} §External integrations and resilience). It is the project's
 * first outbound-resilience pattern, implemented in-process (no resilience4j — Rule Zero) and
 * driven by the injected {@link Clock} so it is testable without sleeping.
 *
 * <p>States: {@code CLOSED} (calls flow) → after {@code failureThreshold} consecutive failures →
 * {@code OPEN} (calls are short-circuited until {@code cooldown} elapses) → {@code HALF_OPEN} (one
 * trial call allowed) → {@code CLOSED} on success, back to {@code OPEN} on failure. A
 * short-circuited call raises {@link CircuitOpenException} <strong>without</strong> hitting the
 * portal, so a degraded dependency cannot keep hammering the application.
 *
 * <p>Thread-safety is light (single-instance, ADR 0002): the counters are atomic and transitions
 * are logged. It is not a distributed breaker — it does not need to be.
 */
@Slf4j
public class PointClockCircuitBreaker {

  /** Breaker states. */
  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final Duration cooldown;
  private final Clock clock;

  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicReference<Instant> openedAt = new AtomicReference<>(null);

  /**
   * @param failureThreshold consecutive failures that trip the breaker open (≥ 1)
   * @param cooldown how long the breaker stays open before allowing a trial call
   * @param clock the controlled clock (UTC)
   */
  public PointClockCircuitBreaker(int failureThreshold, Duration cooldown, Clock clock) {
    this.failureThreshold = Math.max(1, failureThreshold);
    this.cooldown = cooldown;
    this.clock = clock;
  }

  /**
   * Whether a call is currently allowed. If the breaker is OPEN and the cooldown has elapsed, it
   * transitions to HALF_OPEN and allows a single trial. While OPEN within cooldown, calls are
   * refused.
   *
   * @return {@code true} if the call may proceed
   */
  public synchronized boolean allowRequest() {
    State current = state.get();
    if (current == State.CLOSED || current == State.HALF_OPEN) {
      return true;
    }
    // OPEN: allow a trial once the cooldown has elapsed.
    Instant opened = openedAt.get();
    if (opened != null && !clock.instant().isBefore(opened.plus(cooldown))) {
      state.set(State.HALF_OPEN);
      log.info("PointClockCircuitBreaker transition OPEN -> HALF_OPEN (cooldown elapsed)");
      return true;
    }
    return false;
  }

  /** Guards a call: short-circuits with {@link CircuitOpenException} when the breaker is open. */
  public void guard() {
    if (!allowRequest()) {
      throw new CircuitOpenException();
    }
  }

  /** Records a successful call: closes the breaker and resets the failure count. */
  public synchronized void recordSuccess() {
    consecutiveFailures.set(0);
    if (state.get() != State.CLOSED) {
      log.info("PointClockCircuitBreaker transition {} -> CLOSED (success)", state.get());
    }
    state.set(State.CLOSED);
    openedAt.set(null);
  }

  /**
   * Records a failed call: trips the breaker open once the threshold of consecutive failures hits.
   */
  public synchronized void recordFailure() {
    int failures = consecutiveFailures.incrementAndGet();
    if (state.get() == State.HALF_OPEN || failures >= failureThreshold) {
      if (state.get() != State.OPEN) {
        log.warn(
            "PointClockCircuitBreaker transition {} -> OPEN (consecutiveFailures={})",
            state.get(),
            failures);
      }
      state.set(State.OPEN);
      openedAt.set(clock.instant());
    }
  }

  /** The current state (observability). */
  public State state() {
    return state.get();
  }

  /** Raised when a call is short-circuited because the breaker is open. */
  public static class CircuitOpenException extends RuntimeException {
    public CircuitOpenException() {
      super("point-clock circuit breaker is open");
    }
  }
}
