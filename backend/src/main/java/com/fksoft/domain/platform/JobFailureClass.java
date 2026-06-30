package com.fksoft.domain.platform;

/**
 * Classification of a job failure (SPEC-0023 BR3; messaging-and-integrations.md failure taxonomy).
 * Recorded on a {@link JobStatus#FAILED} run so operators can tell an infrastructure outage from a
 * bug or an integration rejection — the scheduler never collapses these into a silent success.
 */
public enum JobFailureClass {
  TIMEOUT,
  UNAVAILABLE,
  RATE_LIMITED,
  INVALID_RESPONSE,
  BUSINESS_REJECTED,
  UNKNOWN_ERROR
}
