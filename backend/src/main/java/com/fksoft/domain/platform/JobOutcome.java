package com.fksoft.domain.platform;

/**
 * The result a governed job's work returns to the {@link PlatformJobService} (SPEC-0023 BR2): the
 * countable item count for the {@code JobRun} history/metrics. A job with nothing countable returns
 * {@link #none()}.
 *
 * @param items the number of items processed (e.g. flags raised, snapshots collected)
 */
public record JobOutcome(int items) {

  /** An outcome with no countable items. */
  public static JobOutcome none() {
    return new JobOutcome(0);
  }

  /** An outcome with a given item count. */
  public static JobOutcome of(int items) {
    return new JobOutcome(items);
  }
}
