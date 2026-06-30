package com.fksoft.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a job execution (SPEC-0023 — {@code GET /jobs/runs}). Metadata only.
 *
 * @param runId the run id
 * @param job the job name
 * @param startedAt when it started
 * @param finishedAt when it finished (or {@code null} if running)
 * @param status the run status
 * @param items the countable outcome (or {@code null})
 * @param failureClass the failure classification when FAILED (or {@code null})
 * @param correlationId the correlation id of the run (or {@code null})
 */
public record JobRunView(
    UUID runId,
    String job,
    Instant startedAt,
    Instant finishedAt,
    JobStatus status,
    Integer items,
    JobFailureClass failureClass,
    String correlationId) {}
