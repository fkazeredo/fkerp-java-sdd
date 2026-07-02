package com.fksoft.domain.sourcing;

/**
 * Lifecycle of a quarantined inbound quotation (SPEC-0009 BR10, DL-0120). A state machine — stays
 * an enum by the Fase 18 keep criterion (never reference data).
 */
public enum InboundQuarantineStatus {
  /** Rejected at the boundary and waiting for operator action. */
  QUARANTINED,
  /** Successfully replayed after the cause was fixed; a quote was created. */
  REPLAYED,
  /** Discarded by the operator (the payload should not enter the system). */
  DISCARDED
}
