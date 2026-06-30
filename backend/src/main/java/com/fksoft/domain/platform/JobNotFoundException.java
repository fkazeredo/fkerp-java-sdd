package com.fksoft.domain.platform;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a job is referenced by a name that is not in the catalog (SPEC-0023 Error Behavior,
 * {@code platform.job.not-found}). Mapped to {@code 404 Not Found}.
 */
public class JobNotFoundException extends DomainException {

  public JobNotFoundException(String jobName) {
    super("platform.job.not-found", jobName);
  }
}
