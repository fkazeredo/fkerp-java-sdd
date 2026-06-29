package com.fksoft.domain.finance;

/**
 * Lifecycle of a ledger entry (SPEC-0015 BR2): it may be born {@code PROVISIONAL} (a document can
 * still be missing), move to {@code CONFIRMED} once validated, and finally {@code SETTLED} once
 * paid/received (via Payout, SPEC-0017). The allowed transitions are encoded in {@link
 * #canTransitionTo}.
 */
public enum EntryStatus {
  PROVISIONAL,
  CONFIRMED,
  SETTLED;

  /**
   * Whether this status may transition to {@code target} (forward-only:
   * PROVISIONAL→CONFIRMED→SETTLED).
   */
  public boolean canTransitionTo(EntryStatus target) {
    return switch (this) {
      case PROVISIONAL -> target == CONFIRMED;
      case CONFIRMED -> target == SETTLED;
      case SETTLED -> false;
    };
  }
}
