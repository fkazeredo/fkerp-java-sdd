package com.fksoft.domain.platform.internal;

import com.fksoft.domain.platform.JobFailureClass;
import com.fksoft.domain.platform.JobRunView;
import com.fksoft.domain.platform.JobStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A single execution of a governed job (SPEC-0023 BR2/BR3). It records start/finish, the terminal
 * status, the item count, the failure classification (when FAILED) and the correlation id — the
 * audit trail of every run. A run that fails is closed {@link JobStatus#FAILED}, never as success
 * (BR3). The {@code idempotencyKey} is the {@code (job, window)} guard (BR2): a second start for
 * the same window is recorded {@link JobStatus#SKIPPED}. Module-internal.
 */
@Entity
@Table(name = "job_runs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRun {

  @Id private UUID id;

  private String jobName;
  private Instant startedAt;
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  private JobStatus status;

  private Integer items;

  @Enumerated(EnumType.STRING)
  private JobFailureClass failureClass;

  private String correlationId;
  private String idempotencyKey;

  /**
   * Opens a RUNNING job run (BR2).
   *
   * @param jobName the job name
   * @param idempotencyKey the {@code (job, window)} key, or {@code null} when not windowed
   * @param correlationId the run correlation id, or {@code null}
   * @param now the start instant
   * @return a new RUNNING run
   */
  public static JobRun start(
      String jobName, String idempotencyKey, String correlationId, Instant now) {
    JobRun run = new JobRun();
    run.id = UUID.randomUUID();
    run.jobName = jobName;
    run.idempotencyKey = idempotencyKey;
    run.correlationId = correlationId;
    run.startedAt = now;
    run.status = JobStatus.RUNNING;
    return run;
  }

  /**
   * Records a SKIPPED run for an already-processed window (idempotency, BR2). It is closed
   * immediately — no work runs.
   */
  public static JobRun skipped(
      String jobName, String idempotencyKey, String correlationId, Instant now) {
    JobRun run = start(jobName, idempotencyKey, correlationId, now);
    run.status = JobStatus.SKIPPED;
    run.finishedAt = now;
    run.idempotencyKey = null; // do not collide with the real run's unique window key
    return run;
  }

  /** Closes the run as SUCCEEDED with the item count (BR2). */
  public void succeed(Integer items, Instant now) {
    this.status = JobStatus.SUCCEEDED;
    this.items = items;
    this.finishedAt = now;
  }

  /** Closes the run as FAILED with the failure classification (BR3 — never a false success). */
  public void fail(JobFailureClass failureClass, Instant now) {
    this.status = JobStatus.FAILED;
    this.failureClass = failureClass;
    this.finishedAt = now;
  }

  /** The run id. */
  public UUID id() {
    return id;
  }

  /** The run status. */
  public JobStatus status() {
    return status;
  }

  /** Projects to the public read view. */
  public JobRunView toView() {
    return new JobRunView(
        id, jobName, startedAt, finishedAt, status, items, failureClass, correlationId);
  }
}
