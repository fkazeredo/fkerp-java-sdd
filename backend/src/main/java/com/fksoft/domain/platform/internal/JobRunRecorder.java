package com.fksoft.domain.platform.internal;

import com.fksoft.domain.platform.JobFailureClass;
import com.fksoft.domain.platform.JobRunView;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the {@link JobRun} lifecycle in <strong>independent</strong> ({@code REQUIRES_NEW})
 * transactions (SPEC-0023 BR3; DL-0075). Kept as a separate component (not inline in {@code
 * PlatformJobService}) on purpose: a self-invoked {@code @Transactional} method bypasses the Spring
 * proxy, so the new-transaction boundary would be ignored. By living here and being injected, each
 * open/close commits on its own — a failing job's FAILED row survives even though the job's own
 * work transaction rolled back, so a failure can never be lost or masked as success.
 */
@Component
@RequiredArgsConstructor
public class JobRunRecorder {

  private final JobRunRepository jobRunRepository;
  private final ScheduledJobRepository scheduledJobRepository;
  private final Clock clock;

  /** Opens and commits a RUNNING run; returns its id. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID openRun(String jobName, String idempotencyKey) {
    JobRun run = JobRun.start(jobName, idempotencyKey, MDC.get("correlationId"), clock.instant());
    jobRunRepository.save(run);
    return run.id();
  }

  /** Commits a SKIPPED run for an already-processed window (idempotency). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public JobRunView recordSkipped(String jobName, String idempotencyKey) {
    JobRun run = JobRun.skipped(jobName, idempotencyKey, MDC.get("correlationId"), clock.instant());
    return jobRunRepository.save(run).toView();
  }

  /** Commits the run as SUCCEEDED with the item count. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public JobRunView closeSucceeded(UUID runId, Integer items) {
    JobRun run = jobRunRepository.findById(runId).orElseThrow();
    run.succeed(items, clock.instant());
    return jobRunRepository.save(run).toView();
  }

  /** Commits the run as FAILED with the classification (BR3 — never a false success). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public JobRunView closeFailed(UUID runId, JobFailureClass failureClass) {
    JobRun run = jobRunRepository.findById(runId).orElseThrow();
    run.fail(failureClass, clock.instant());
    return jobRunRepository.save(run).toView();
  }

  /** Updates the catalog job's {@code lastRunAt} (best-effort). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markJobRan(String jobName) {
    scheduledJobRepository.findByName(jobName).ifPresent(job -> job.markRan(clock.instant()));
  }
}
