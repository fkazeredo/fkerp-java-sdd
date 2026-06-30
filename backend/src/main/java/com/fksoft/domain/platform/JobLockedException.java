package com.fksoft.domain.platform;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a job run cannot start because another instance already holds the job's lock
 * (SPEC-0023 BR2; Error Behavior {@code platform.job.locked}). Mapped to {@code 409 Conflict}. This
 * is the "one instance at a time" guarantee surfacing as a clear domain error, never a raw database
 * exception.
 */
public class JobLockedException extends DomainException {

  public JobLockedException(String jobName) {
    super("platform.job.locked", jobName);
  }
}
