package com.fksoft.domain.portfolio;

/**
 * The metric a brand goal is measured in (SPEC-0020 BR3): {@link #VOLUME} (count of confirmed
 * sales) or {@link #REVENUE} (realized spread in BRL — the Acme's real revenue, OVERVIEW Part 3.2).
 * The realized side is projected from sales events (BR4/DL-0062): {@code BookingConfirmed} feeds
 * VOLUME, {@code SpreadRealized} feeds REVENUE.
 */
public enum GoalMetric {
  /** A count of confirmed sales for the brand. */
  VOLUME,
  /** The realized spread (BRL) attributed to the brand. */
  REVENUE
}
