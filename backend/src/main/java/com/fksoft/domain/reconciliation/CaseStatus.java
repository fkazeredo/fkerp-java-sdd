package com.fksoft.domain.reconciliation;

/**
 * Status of a reconciliation case (BR1/BR2/BR6/BR7): {@link #OPEN} when created, {@link
 * #PARTIALLY_SETTLED} when only some legs are realized, {@link #SETTLED} when all are, {@link
 * #DISCREPANCY} when the realized spread diverges beyond tolerance, {@link #CANCELLED} when the
 * booking was cancelled. External value is the name.
 */
public enum CaseStatus {
  OPEN,
  PARTIALLY_SETTLED,
  SETTLED,
  DISCREPANCY,
  CANCELLED
}
