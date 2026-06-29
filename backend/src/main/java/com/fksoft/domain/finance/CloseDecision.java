package com.fksoft.domain.finance;

import java.util.List;

/**
 * The answer the Compliance gives to "can this period be closed?" (SPEC-0015 BR3; SPEC-0008 BR6).
 * When {@code canClose} is false, {@code pending} lists the non-conformant entries and what each is
 * missing. Returned by the {@link CloseGuard} port.
 *
 * @param canClose whether the period may be closed
 * @param pending the blocking entries (empty when {@code canClose} is true)
 */
public record CloseDecision(boolean canClose, List<PendingEntry> pending) {

  public CloseDecision {
    pending = pending == null ? List.of() : List.copyOf(pending);
  }

  /** A decision that allows the close (no pending entries). */
  public static CloseDecision allowed() {
    return new CloseDecision(true, List.of());
  }

  /** A decision that blocks the close with the given pending entries. */
  public static CloseDecision blocked(List<PendingEntry> pending) {
    return new CloseDecision(false, pending);
  }
}
