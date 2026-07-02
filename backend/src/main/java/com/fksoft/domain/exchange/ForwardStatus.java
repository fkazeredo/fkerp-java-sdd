package com.fksoft.domain.exchange;

/**
 * Lifecycle of an FX forward contract (SPEC-0032, DL-0130). A state machine — stays an enum by the
 * Fase 18 keep criterion (never reference data).
 */
public enum ForwardStatus {
  /** In force: counts as coverage against the open exposure. */
  OPEN,
  /** Settled at maturity with an effective rate; the realized side feeds the FX reports. */
  SETTLED,
  /** Cancelled before settlement; stops counting as coverage. */
  CANCELLED
}
