package com.fksoft.domain.people;

/**
 * Lifecycle of a journey discrepancy in the treatment queue (SPEC-0022 BR4; DL-0071): {@code OPEN}
 * (awaiting human treatment) or {@code RESOLVED} (treated — a manual record of who/when, with no
 * automatic recalculation).
 */
public enum DiscrepancyStatus {
  OPEN,
  RESOLVED
}
