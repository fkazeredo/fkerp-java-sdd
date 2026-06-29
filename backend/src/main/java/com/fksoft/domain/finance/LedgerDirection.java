package com.fksoft.domain.finance;

/**
 * Direction of a {@link com.fksoft.domain.finance.internal.LedgerEntry} (SPEC-0015 BR1): money the
 * Acme owes ({@code PAYABLE}) or money it is owed ({@code RECEIVABLE}).
 */
public enum LedgerDirection {
  PAYABLE,
  RECEIVABLE
}
