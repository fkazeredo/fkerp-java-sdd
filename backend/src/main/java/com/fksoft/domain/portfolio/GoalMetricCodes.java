package com.fksoft.domain.portfolio;

/**
 * The {@code GOAL_METRIC} code constants whose behavior the domain wires (SPEC-0031 BR5; DL-0116).
 * After {@code GoalMetric} became an editable cadastro, the metric still drives the realized
 * projection (SPEC-0020 BR4/DL-0062): {@code BookingConfirmed} feeds {@link #VOLUME}, {@code
 * SpreadRealized} feeds {@link #REVENUE}. The target shape (a BRL amount for REVENUE, a count for
 * VOLUME) and the progress projection also branch on these two codes. The cadastro owns the
 * extensible set + labels; this class owns the wired behavior.
 *
 * <p>These two codes are load-bearing: a goal defined with an unknown metric code has no realized
 * projection wired, so REVENUE/VOLUME stay the only metrics that compute progress until a later
 * slice wires a new one. The service validates the metric against the cadastro on write, so an
 * unknown/inactive code is rejected (422) before a goal is ever stored.
 */
public final class GoalMetricCodes {

  /** A count of confirmed sales for the brand — fed by {@code BookingConfirmed}. */
  public static final String VOLUME = "VOLUME";

  /** The realized spread (BRL) attributed to the brand — fed by {@code SpreadRealized}. */
  public static final String REVENUE = "REVENUE";

  private GoalMetricCodes() {}
}
