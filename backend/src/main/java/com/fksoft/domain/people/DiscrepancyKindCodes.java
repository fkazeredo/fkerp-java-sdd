package com.fksoft.domain.people;

/**
 * The {@code DISCREPANCY_KIND} code constants the domain wires (SPEC-0031 BR5; DL-0118). After
 * {@code DiscrepancyKind} became an editable cadastro, the kinds the {@code JourneyCalculator}
 * detects are still a closed, testable set (SPEC-0022 BR4; DL-0071). This kind is
 * <strong>system-produced</strong>: the calculator emits these constants from the operational
 * snapshot; it never arrives as a write payload, so there is no write validation — it is a cadastro
 * only so the labels are editable and the screens render them (same precedent as the {@code
 * INSIGHT_*} kinds of DL-0116). The cadastro owns the extensible set + labels; this class owns the
 * wired values the calculator emits.
 */
public final class DiscrepancyKindCodes {

  /** An odd number of captured punches — an entry without its matching exit. */
  public static final String ODD_PUNCH = "ODD_PUNCH";

  /** Fewer punches than expected for the period — a missing mark. */
  public static final String MISSING_PUNCH = "MISSING_PUNCH";

  /** A journey that cannot be true (no worked time with punches, or beyond a sane ceiling). */
  public static final String INCOHERENT_JOURNAL = "INCOHERENT_JOURNAL";

  private DiscrepancyKindCodes() {}
}
