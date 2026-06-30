package com.fksoft.domain.platform;

/**
 * Outcome of a job execution (SPEC-0023 BR2/BR3). A failed job is recorded {@link #FAILED}, never
 * masked as success (BR3); a duplicate run for an already-processed window is {@link #SKIPPED}
 * (idempotency, BR2); a run blocked by the lock never reaches a {@code JobRun} (it is rejected with
 * {@link JobLockedException}).
 */
public enum JobStatus {

  /** The run is in progress. */
  RUNNING,

  /** The run finished successfully. */
  SUCCEEDED,

  /** The run failed — the failure is recorded, never hidden (BR3). */
  FAILED,

  /** The run was skipped because the window was already processed (idempotency, BR2). */
  SKIPPED
}
