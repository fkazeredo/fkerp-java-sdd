package com.fksoft.domain.platform;

import java.time.Instant;

/**
 * Public read view of a catalog job (SPEC-0023 — {@code GET /jobs}).
 *
 * @param name the stable job name
 * @param cron the documented schedule
 * @param enabled whether the job is enabled
 * @param ownerModule the module that owns the job's logic (BR6)
 * @param lastRunAt the last time it ran (or {@code null})
 */
public record ScheduledJobView(
    String name, String cron, boolean enabled, String ownerModule, Instant lastRunAt) {}
