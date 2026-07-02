package com.fksoft.infra.integration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * A small, reusable circuit breaker for <strong>outbound</strong> integrations (Fase 19e, DL-0127):
 * after {@code failureThreshold} consecutive failures it trips OPEN and short-circuits calls for a
 * {@code cooldown}, then allows a single HALF_OPEN trial. It generalizes the in-process pattern the
 * point-clock crawler introduced (DL-0031) so a real HTTP adapter (e.g. the municipal NFS-e client)
 * gets the same protection without pulling in resilience4j (Rule Zero). Time is a controlled {@link
 * Clock}, so cooldown transitions are deterministically testable.
 */
@Slf4j
public class OutboundCircuitBreaker {

  /** Breaker states. */
  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final String name;
  private final int failureThreshold;
  private final Duration cooldown;
  private final Clock clock;

  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicReference<Instant> openedAt = new AtomicReference<>(null);

  public OutboundCircuitBreaker(String name, int failureThreshold, Duration cooldown, Clock clock) {
    this.name = name;
    this.failureThreshold = Math.max(1, failureThreshold);
    this.cooldown = cooldown;
    this.clock = clock;
  }

  /** Whether a call may proceed (transitions OPEN → HALF_OPEN when the cooldown has elapsed). */
  public synchronized boolean allowRequest() {
    State current = state.get();
    if (current == State.CLOSED || current == State.HALF_OPEN) {
      return true;
    }
    Instant opened = openedAt.get();
    if (opened != null && !clock.instant().isBefore(opened.plus(cooldown))) {
      state.set(State.HALF_OPEN);
      log.info("{} circuit breaker OPEN -> HALF_OPEN (cooldown elapsed)", name);
      return true;
    }
    return false;
  }

  /** Short-circuits with {@link CircuitOpenException} when the breaker is open. */
  public void guard() {
    if (!allowRequest()) {
      throw new CircuitOpenException(name);
    }
  }

  /** Records a success: closes the breaker and resets the failure count. */
  public synchronized void recordSuccess() {
    consecutiveFailures.set(0);
    if (state.get() != State.CLOSED) {
      log.info("{} circuit breaker {} -> CLOSED (success)", name, state.get());
    }
    state.set(State.CLOSED);
    openedAt.set(null);
  }

  /** Records a failure: trips OPEN once the consecutive-failure threshold is reached. */
  public synchronized void recordFailure() {
    int failures = consecutiveFailures.incrementAndGet();
    if (state.get() == State.HALF_OPEN || failures >= failureThreshold) {
      if (state.get() != State.OPEN) {
        log.warn(
            "{} circuit breaker {} -> OPEN (consecutiveFailures={})", name, state.get(), failures);
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
    public CircuitOpenException(String name) {
      super(name + " circuit breaker is open");
    }
  }
}
