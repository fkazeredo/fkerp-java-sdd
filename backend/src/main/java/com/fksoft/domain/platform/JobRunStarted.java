package com.fksoft.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: a governed job run has started (SPEC-0023 Events). Carries only metadata — consumed
 * by the system audit (DL-0077) and observability.
 *
 * @param runId the job-run id
 * @param job the job name
 * @param occurredAt when it started
 */
public record JobRunStarted(UUID runId, String job, Instant occurredAt) {}
