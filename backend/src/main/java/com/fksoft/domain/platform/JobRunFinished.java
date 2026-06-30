package com.fksoft.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: a governed job run has finished (SPEC-0023 Events). Carries the terminal status and
 * optional item count — consumed by the system audit (DL-0077), observability and the Intelligence
 * (operational hygiene). A {@link JobStatus#FAILED} run is reported as such, never as success
 * (BR3).
 *
 * @param runId the job-run id
 * @param job the job name
 * @param status the terminal status (SUCCEEDED | FAILED | SKIPPED)
 * @param items the countable outcome, or {@code null}
 * @param occurredAt when it finished
 */
public record JobRunFinished(
    UUID runId, String job, JobStatus status, Integer items, Instant occurredAt) {}
