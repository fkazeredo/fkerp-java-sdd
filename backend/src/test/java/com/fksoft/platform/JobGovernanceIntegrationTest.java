package com.fksoft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.platform.JobLock;
import com.fksoft.domain.platform.JobLockedException;
import com.fksoft.domain.platform.JobOutcome;
import com.fksoft.domain.platform.JobRunView;
import com.fksoft.domain.platform.JobStatus;
import com.fksoft.domain.platform.PlatformJobService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the job governance (SPEC-0023 BR2/BR3; DL-0075/DL-0076). They prove the
 * registry runs idempotently per window, locks one instance at a time (a concurrent run sees {@code
 * locked}), and records a failing job as {@code JobRun FAILED} (never a false success).
 */
class JobGovernanceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PlatformJobService jobService;
  @Autowired private JobLock jobLock;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM job_runs");
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  @Test
  void aSuccessfulRunIsRecordedSucceededWithItsItemCount() {
    JobRunView run =
        jobService.runWithGovernance("aftersales-sla-sweep", "2026-06-30", () -> JobOutcome.of(3));

    assertThat(run.status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(run.items()).isEqualTo(3);
    assertThat(run.finishedAt()).isNotNull();
  }

  @Test
  void aSecondRunForTheSameWindowIsSkippedIdempotently() {
    jobService.runWithGovernance("asset-license-expiry", "2026-06-30", () -> JobOutcome.of(1));
    JobRunView second =
        jobService.runWithGovernance("asset-license-expiry", "2026-06-30", () -> JobOutcome.of(99));

    assertThat(second.status()).isEqualTo(JobStatus.SKIPPED);
    // Only one SUCCEEDED run exists for that window; the work did not run twice.
    Integer succeeded =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM job_runs WHERE job_name='asset-license-expiry' AND status='SUCCEEDED'",
            Integer.class);
    assertThat(succeeded).isEqualTo(1);
  }

  @Test
  void aFailingJobIsRecordedFailedNotSuccess() {
    assertThatThrownBy(
            () ->
                jobService.runWithGovernance(
                    "retention-expiry",
                    "2026-06-30",
                    () -> {
                      throw new IllegalStateException("boom unavailable");
                    }))
        .isInstanceOf(IllegalStateException.class);

    // BR3: the FAILED run is persisted (it survived the work's rollback) and is NOT a success.
    Integer failed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM job_runs WHERE job_name='retention-expiry' AND status='FAILED'",
            Integer.class);
    assertThat(failed).isEqualTo(1);
    Integer succeeded =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM job_runs WHERE job_name='retention-expiry' AND status='SUCCEEDED'",
            Integer.class);
    assertThat(succeeded).isEqualTo(0);
    String failureClass =
        jdbcTemplate.queryForObject(
            "SELECT failure_class FROM job_runs WHERE status='FAILED'", String.class);
    assertThat(failureClass).isEqualTo("UNAVAILABLE");
  }

  @Test
  void twoConcurrentRunsOfTheSameJobOneRunsTheOtherSeesLocked() throws Exception {
    // BR2: while one run holds the lock (work blocked on a latch), a concurrent run is rejected.
    CountDownLatch insideLock = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<JobRunView> holder =
          pool.submit(
              () ->
                  jobLock.runExclusively(
                      "certificate-expiry",
                      () -> {
                        insideLock.countDown();
                        try {
                          release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return new JobRunView(
                            java.util.UUID.randomUUID(),
                            "certificate-expiry",
                            java.time.Instant.now(),
                            java.time.Instant.now(),
                            JobStatus.SUCCEEDED,
                            0,
                            null,
                            null);
                      }));

      assertThat(insideLock.await(10, TimeUnit.SECONDS)).isTrue();

      AtomicReference<Throwable> contender = new AtomicReference<>();
      Future<?> blocked =
          pool.submit(
              () -> {
                try {
                  jobLock.runExclusively("certificate-expiry", () -> "should-not-run");
                } catch (Throwable t) {
                  contender.set(t);
                }
              });
      blocked.get(10, TimeUnit.SECONDS);
      assertThat(contender.get()).isInstanceOf(JobLockedException.class);

      release.countDown();
      assertThat(holder.get(10, TimeUnit.SECONDS).status()).isEqualTo(JobStatus.SUCCEEDED);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void theRunHistoryIsFilterableByJobAndStatus() {
    jobService.runWithGovernance("aftersales-sla-sweep", "w1", () -> JobOutcome.of(1));
    jobService.runWithGovernance("asset-license-expiry", "w1", () -> JobOutcome.of(2));

    var slaRuns = jobService.runs("aftersales-sla-sweep", null, PageRequest.of(0, 20));
    assertThat(slaRuns.getContent()).allMatch(r -> r.job().equals("aftersales-sla-sweep"));

    var succeeded = jobService.runs(null, JobStatus.SUCCEEDED, PageRequest.of(0, 20));
    assertThat(succeeded.getContent()).allMatch(r -> r.status() == JobStatus.SUCCEEDED);
  }
}
