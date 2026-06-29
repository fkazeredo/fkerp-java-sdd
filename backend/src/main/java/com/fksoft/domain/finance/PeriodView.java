package com.fksoft.domain.finance;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Public read view of an accounting period (SPEC-0015): its status and the AP/AR totals aggregated
 * <strong>per currency</strong> (DL-0013 — the ledger never sums different currencies).
 *
 * @param period the period ({@code YYYY-MM})
 * @param status OPEN, CLOSING or CLOSED
 * @param payableTotals the PAYABLE totals, one per currency
 * @param receivableTotals the RECEIVABLE totals, one per currency
 * @param closedAt when it was closed, or {@code null}
 */
public record PeriodView(
    String period,
    PeriodStatus status,
    List<Money> payableTotals,
    List<Money> receivableTotals,
    Instant closedAt) {

  public PeriodView {
    payableTotals = payableTotals == null ? List.of() : List.copyOf(payableTotals);
    receivableTotals = receivableTotals == null ? List.of() : List.copyOf(receivableTotals);
  }
}
