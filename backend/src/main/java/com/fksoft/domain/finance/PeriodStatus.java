package com.fksoft.domain.finance;

/**
 * Status of an {@link com.fksoft.domain.finance.AccountingPeriod} (SPEC-0015 BR3): {@code OPEN}
 * accepts entries; {@code CLOSING} is the transient state while the close is being evaluated (the
 * Compliance veto is consulted); {@code CLOSED} is sealed and rejects new entries (BR4).
 */
public enum PeriodStatus {
  OPEN,
  CLOSING,
  CLOSED
}
