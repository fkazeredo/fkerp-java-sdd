package com.fksoft.domain.compliance;

/**
 * When a document requirement must be satisfied (DL-0012): {@code AT_REGISTRATION} is required for
 * the entry to be conformant and for the month to close; {@code AT_SETTLEMENT} is required only at
 * settlement (e.g. the payment proof). The close-check considers only {@code AT_REGISTRATION}.
 */
public enum RequirementPhase {
  AT_REGISTRATION,
  AT_SETTLEMENT
}
