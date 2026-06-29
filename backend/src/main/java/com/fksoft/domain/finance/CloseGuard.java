package com.fksoft.domain.finance;

/**
 * Port consumed by Finance and implemented by Compliance (SPEC-0015 BR6; SPEC-0008 BR6): asks
 * whether a period may be closed. Finance owns the period lock and the calendar; Compliance owns
 * the document rule and answers the veto. Keeping the contract here (in the consumer) lets Finance
 * build and stay green before Compliance exists — a default {@link AlwaysAllowsCloseGuard} answers
 * "allowed" until the real implementation lands (a traceable seam, SPEC-0008).
 */
public interface CloseGuard {

  /**
   * Whether the given period may be closed, and the blocking entries if not.
   *
   * @param period the period to evaluate ({@code YYYY-MM})
   * @return the close decision
   */
  CloseDecision checkClose(AccountingPeriodId period);
}
