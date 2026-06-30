package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for the job-run history (SPEC-0023 — {@code GET /jobs/runs}). Reads
 * are paginated and filterable by job and status. Only the {@code platform} domain reaches it
 * (Spring Modulith). The {@code (job_name, idempotency_key)} unique index (V28) enforces window
 * idempotency (BR2) at the database — a duplicate non-SKIPPED insert raises a constraint violation
 * the service pre-empts with a {@code exists} check.
 */
@ModuleInternal
public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

  Page<JobRun> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

  Page<JobRun> findByJobNameAndStatusOrderByStartedAtDesc(
      String jobName, JobStatus status, Pageable pageable);

  Page<JobRun> findByStatusOrderByStartedAtDesc(JobStatus status, Pageable pageable);

  Page<JobRun> findAllByOrderByStartedAtDesc(Pageable pageable);

  /** Whether a non-SKIPPED run already exists for this {@code (job, window)} (idempotency, BR2). */
  boolean existsByJobNameAndIdempotencyKeyAndStatusNot(
      String jobName, String idempotencyKey, JobStatus status);
}
