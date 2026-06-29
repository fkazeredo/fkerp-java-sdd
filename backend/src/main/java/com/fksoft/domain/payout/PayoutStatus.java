package com.fksoft.domain.payout;

import java.util.Set;

/**
 * Lifecycle of a payout / installment (SPEC-0017 BR2): {@code PENDING → EXECUTING → EXECUTED |
 * FAILED}. The execution is a financial transition (pessimistic locking), and a gateway failure
 * lands an explicit {@code FAILED} — never a false {@code EXECUTED} (BR2). A {@code FAILED}
 * installment may be retried, going back to {@code EXECUTING} (BR3 retries are safe via
 * idempotency).
 */
public enum PayoutStatus {
  PENDING(Set.of("EXECUTING")),
  EXECUTING(Set.of("EXECUTED", "FAILED")),
  EXECUTED(Set.of()),
  FAILED(Set.of("EXECUTING"));

  private final Set<String> allowedNext;

  PayoutStatus(Set<String> allowedNext) {
    this.allowedNext = allowedNext;
  }

  /** Whether this status may transition to {@code target} (BR2 state machine). */
  public boolean canTransitionTo(PayoutStatus target) {
    return allowedNext.contains(target.name());
  }
}
