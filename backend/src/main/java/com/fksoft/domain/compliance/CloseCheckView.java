package com.fksoft.domain.compliance;

import com.fksoft.domain.finance.PendingEntry;
import java.util.List;

/**
 * Public read view of the period close-check (SPEC-0008): whether the period may close and, if not,
 * the non-conformant entries and what each is missing. Consumed by the Finance close (via the
 * {@link com.fksoft.domain.finance.CloseGuard} port) and exposed directly at {@code GET
 * /api/compliance/close-check}.
 *
 * @param period the period checked ({@code YYYY-MM})
 * @param canClose whether the period may be closed
 * @param pending the blocking entries (empty when {@code canClose} is true)
 */
public record CloseCheckView(String period, boolean canClose, List<PendingEntry> pending) {

  public CloseCheckView {
    pending = pending == null ? List.of() : List.copyOf(pending);
  }
}
