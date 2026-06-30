package com.fksoft.domain.platform;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for job governance (SPEC-0023 BR2/BR3; DL-0075/DL-0076). It runs a job's work
 * through the registry: a distributed {@link JobLock} (one instance at a time, BR2), idempotency by
 * {@code (job, window)} (a second run for the same window is recorded {@link JobStatus#SKIPPED}),
 * and a {@link JobRun} with start/finish/status/items/correlation id. A failed job is recorded
 * {@link JobStatus#FAILED} and the failure is re-raised — never masked as success (BR3). The job's
 * LOGIC stays in its owner module; this service only governs.
 *
 * <p>The {@link JobRun} lifecycle is delegated to {@link JobRunRecorder}, whose {@code
 * REQUIRES_NEW} transactions commit independently of the work's own transaction — so a failing work
 * cannot roll back its own FAILED audit row (the boundary is honored because the recorder is a
 * separate proxied bean, not a self-invoked method).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformJobService {

  private final ScheduledJobRepository scheduledJobRepository;
  private final JobRunRepository jobRunRepository;
  private final JobRunRecorder jobRunRecorder;
  private final JobLock jobLock;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * Runs a job's work under governance (BR2/BR3). Acquires the job lock (rejecting a concurrent run
   * with {@link JobLockedException}), enforces window idempotency, opens a RUNNING {@link JobRun},
   * runs the work and closes the run SUCCEEDED (with the item count) or FAILED (with the
   * classification, re-raising the failure).
   *
   * @param jobName the catalog job name
   * @param window the idempotency window (e.g. {@code 2026-06}), or {@code null} to always run
   * @param work the job's work, returning its countable outcome
   * @return the recorded run view
   * @throws JobLockedException when another instance already holds the job's lock
   */
  public JobRunView runWithGovernance(String jobName, String window, Supplier<JobOutcome> work) {
    return jobLock.runExclusively(jobName, () -> runLocked(jobName, window, work));
  }

  private JobRunView runLocked(String jobName, String window, Supplier<JobOutcome> work) {
    String idempotencyKey = window == null || window.isBlank() ? null : jobName + ":" + window;
    if (idempotencyKey != null
        && jobRunRepository.existsByJobNameAndIdempotencyKeyAndStatusNot(
            jobName, idempotencyKey, JobStatus.SKIPPED)) {
      JobRunView skipped = jobRunRecorder.recordSkipped(jobName, idempotencyKey);
      log.info("JobRunSkipped job={} window={} (already processed)", jobName, window);
      return skipped;
    }

    UUID runId = jobRunRecorder.openRun(jobName, idempotencyKey);
    events.publishEvent(new JobRunStarted(runId, jobName, clock.instant()));
    log.info("JobRunStarted runId={} job={} window={}", runId, jobName, window);
    try {
      JobOutcome outcome = work.get();
      JobRunView view = jobRunRecorder.closeSucceeded(runId, outcome.items());
      jobRunRecorder.markJobRan(jobName);
      events.publishEvent(
          new JobRunFinished(
              runId, jobName, JobStatus.SUCCEEDED, outcome.items(), clock.instant()));
      log.info(
          "JobRunFinished runId={} job={} status=SUCCEEDED items={}",
          runId,
          jobName,
          outcome.items());
      return view;
    } catch (RuntimeException failure) {
      JobFailureClass failureClass = classify(failure);
      jobRunRecorder.closeFailed(runId, failureClass);
      events.publishEvent(
          new JobRunFinished(runId, jobName, JobStatus.FAILED, null, clock.instant()));
      // BR3: the failure is recorded AND re-raised — never reported as success.
      log.warn(
          "JobRunFinished runId={} job={} status=FAILED class={}", runId, jobName, failureClass);
      throw failure;
    }
  }

  // --- registry / history reads ---

  /** The job catalog (SPEC-0023 — {@code GET /jobs}). */
  @Transactional(readOnly = true)
  public List<ScheduledJobView> catalog() {
    return scheduledJobRepository.findAllByOrderByNameAsc().stream()
        .map(ScheduledJob::toView)
        .toList();
  }

  /**
   * The run history (SPEC-0023 — {@code GET /jobs/runs}), filterable by job and status, paginated.
   *
   * @param job the job filter, or {@code null} for all jobs
   * @param status the status filter, or {@code null} for all statuses
   * @param pageable the page request
   * @return the page of run views
   */
  @Transactional(readOnly = true)
  public Page<JobRunView> runs(String job, JobStatus status, Pageable pageable) {
    Page<JobRun> page;
    if (job != null && status != null) {
      page = jobRunRepository.findByJobNameAndStatusOrderByStartedAtDesc(job, status, pageable);
    } else if (job != null) {
      page = jobRunRepository.findByJobNameOrderByStartedAtDesc(job, pageable);
    } else if (status != null) {
      page = jobRunRepository.findByStatusOrderByStartedAtDesc(status, pageable);
    } else {
      page = jobRunRepository.findAllByOrderByStartedAtDesc(pageable);
    }
    return page.map(JobRun::toView);
  }

  /**
   * Validates a job name exists in the catalog (for the manual trigger).
   *
   * @param jobName the job name
   * @return the catalog job view
   * @throws JobNotFoundException when the name is not in the catalog
   */
  @Transactional(readOnly = true)
  public ScheduledJobView requireJob(String jobName) {
    return scheduledJobRepository
        .findByName(jobName)
        .map(ScheduledJob::toView)
        .orElseThrow(() -> new JobNotFoundException(jobName));
  }

  private static JobFailureClass classify(RuntimeException failure) {
    String name = failure.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    String message =
        failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
    if (name.contains("timeout") || message.contains("timeout")) {
      return JobFailureClass.TIMEOUT;
    }
    if (name.contains("unavailable") || message.contains("unavailable")) {
      return JobFailureClass.UNAVAILABLE;
    }
    return JobFailureClass.UNKNOWN_ERROR;
  }
}
