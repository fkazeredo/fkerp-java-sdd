package com.fksoft.domain.sourcing;

/**
 * How integrated the source of an offer is (SPEC-0009 BR1). External (API/persistence) value is the
 * constant name.
 */
public enum IntegrationLevel {

  /** No integration — the offer is handled manually. */
  NONE,

  /** Inbound only — the external system feeds the ERP (e.g. the quotation-site webhook). */
  INBOUND,

  /** Two-way integration (the ERP both reads from and writes to the external system). */
  BIDIRECTIONAL
}
