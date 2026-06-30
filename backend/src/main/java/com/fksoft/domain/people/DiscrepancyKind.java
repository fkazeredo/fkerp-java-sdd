package com.fksoft.domain.people;

/**
 * Kinds of journey discrepancy the HR side detects from the operational snapshot (SPEC-0022 BR4;
 * DL-0071). A closed catalog (minimization, testable): {@code ODD_PUNCH} (an odd number of punches
 * — an entry without its matching exit), {@code MISSING_PUNCH} (fewer punches than expected for the
 * period — a missing mark) and {@code INCOHERENT_JOURNAL} (a journey that cannot be true — e.g. no
 * worked time with punches present, or worked time beyond a sane per-period ceiling). Detecting a
 * discrepancy raises an alert; it never auto-corrects (BR4).
 */
public enum DiscrepancyKind {
  ODD_PUNCH,
  MISSING_PUNCH,
  INCOHERENT_JOURNAL
}
