package com.fksoft.domain.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * Operational trial-balance of an accounting period (SPEC-0015 BR10, DL-0043): the AP/AR balance
 * aggregated <strong>per currency</strong> (DL-0013 — never summing different currencies) plus the
 * entry counts per lifecycle status. The {@code net} is the operational balance ({@code receivable
 * − payable}) of the cash book — <em>not</em> an accounting result (this module is a cash book, not
 * a full general ledger; DL-0042).
 *
 * @param period the period ({@code YYYY-MM})
 * @param status OPEN, CLOSING or CLOSED
 * @param balances the per-currency balances (empty when the period has no entries)
 * @param provisionalCount how many entries are PROVISIONAL
 * @param confirmedCount how many entries are CONFIRMED
 * @param settledCount how many entries are SETTLED
 */
public record TrialBalanceView(
    String period,
    PeriodStatus status,
    List<CurrencyBalance> balances,
    long provisionalCount,
    long confirmedCount,
    long settledCount) {

  public TrialBalanceView {
    balances = balances == null ? List.of() : List.copyOf(balances);
  }

  /**
   * The AP/AR balance for a single currency (DL-0043): payable and receivable totals and their net
   * ({@code receivable − payable}), all in that currency, scale 2.
   *
   * @param currency the ISO currency code
   * @param payable the PAYABLE total in this currency
   * @param receivable the RECEIVABLE total in this currency
   * @param net the operational net ({@code receivable − payable})
   */
  public record CurrencyBalance(
      String currency, BigDecimal payable, BigDecimal receivable, BigDecimal net) {}
}
