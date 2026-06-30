package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Catalog entry for a governed job (SPEC-0023 BR2/DL-0076). The job's LOGIC lives in its {@code
 * ownerModule}; this row is just the registry entry the Platform governs (idempotency, locking,
 * history). Seeded by Flyway (V28). Module-internal.
 */
@Entity
@Table(name = "scheduled_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class ScheduledJob {

  @Id private String name;

  private String cron;
  private boolean enabled;
  private String ownerModule;
  private Instant lastRunAt;

  /** The job name. */
  public String name() {
    return name;
  }

  /** Whether the job is enabled. */
  public boolean enabled() {
    return enabled;
  }

  /** Records that the job ran at the given instant (updates {@code lastRunAt}). */
  public void markRan(Instant when) {
    this.lastRunAt = when;
  }

  /** Projects to the public read view. */
  public ScheduledJobView toView() {
    return new ScheduledJobView(name, cron, enabled, ownerModule, lastRunAt);
  }
}
