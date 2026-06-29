package com.fksoft.domain.intelligence;

/**
 * The kind of insight the DSS produces (SPEC-0013). Each value is a distinct prescriptive lens over
 * the facts the module consumes (read-only). New report types from catalogue 8.2 (churn, forecast,
 * mix…) become new values when each gets its own spec — they are deliberately out of scope here.
 *
 * <ul>
 *   <li>{@code PROMO_FX_ADVISOR} — does the FX freeze promo pay for itself, by agency (BR5,
 *       DL-0035).
 *   <li>{@code OVERRIDE_NUDGE} — distance to the next commission tier (BR6); gated behind a feature
 *       flag until the tier model exists (DL-0036).
 * </ul>
 */
public enum InsightType {
  PROMO_FX_ADVISOR,
  OVERRIDE_NUDGE
}
