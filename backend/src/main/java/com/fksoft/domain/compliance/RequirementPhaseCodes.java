package com.fksoft.domain.compliance;

/**
 * The {@code REQUIREMENT_PHASE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0117). After {@code RequirementPhase} became an editable cadastro, the phase still drives the
 * close-check (DL-0012): only {@link #AT_REGISTRATION} requirements block a period from closing;
 * {@link #AT_SETTLEMENT} requirements (e.g. the payment proof) are needed only at settlement. The
 * close-check queries the seeded {@code document_requirements} by {@link #AT_REGISTRATION}, so this
 * constant is load-bearing. The cadastro owns the extensible set + labels; this class owns the
 * wired phase the close-check reads.
 */
public final class RequirementPhaseCodes {

  /** Required for the entry to be conformant and for the month to close (the close-check phase). */
  public static final String AT_REGISTRATION = "AT_REGISTRATION";

  /** Required only at settlement (e.g. the payment proof) — not checked at close. */
  public static final String AT_SETTLEMENT = "AT_SETTLEMENT";

  private RequirementPhaseCodes() {}
}
