package com.fksoft.domain.aftersales;

import java.util.Set;

/**
 * SupportCase lifecycle state machine (SPEC-0018 Scope/Validation Rules). Each state knows the
 * states it may transition to; an invalid transition is rejected by the aggregate with {@link
 * SupportCaseTransitionInvalidException}. A new case starts {@link #OPEN}; {@link #RESOLVED} and
 * {@link #CLOSED} are terminal-ish ({@code RESOLVED} may still be {@code CLOSED}). The external
 * value is the constant name.
 *
 * <p>The {@code BREACHED} SLA marker is <strong>not</strong> a status here (BR4, DL-0053): a breach
 * is an orthogonal flag/alert that never blocks the workflow, so a breached case keeps moving
 * through these states.
 */
public enum SupportCaseStatus {
  OPEN,
  IN_PROGRESS,
  WAITING,
  RESOLVED,
  CLOSED;

  /** The states this status may transition to. */
  public Set<SupportCaseStatus> allowedNext() {
    return switch (this) {
      case OPEN -> Set.of(IN_PROGRESS, RESOLVED);
      case IN_PROGRESS -> Set.of(WAITING, RESOLVED);
      case WAITING -> Set.of(IN_PROGRESS, RESOLVED);
      case RESOLVED -> Set.of(CLOSED, IN_PROGRESS);
      case CLOSED -> Set.of();
    };
  }

  /** Whether a transition from this status to {@code target} is allowed. */
  public boolean canTransitionTo(SupportCaseStatus target) {
    return allowedNext().contains(target);
  }

  /** Whether this is a terminal status for SLA purposes (no longer counts against the clock). */
  public boolean isTerminal() {
    return this == RESOLVED || this == CLOSED;
  }
}
