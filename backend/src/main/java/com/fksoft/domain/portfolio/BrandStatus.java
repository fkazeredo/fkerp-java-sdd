package com.fksoft.domain.portfolio;

/**
 * Lifecycle status of a represented brand (SPEC-0020 BR1): {@link #ACTIVE} while the Acme
 * represents it commercially, {@link #INACTIVE} once the representation ends. A simple status, not
 * a state machine — there is no complex workflow to justify one (Rule Zero).
 */
public enum BrandStatus {
  /** The brand is currently represented and may be referenced by a sale. */
  ACTIVE,
  /** The representation ended; the brand is kept for history but not actively represented. */
  INACTIVE
}
