package com.fksoft.infra.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Compliance to flag documents approaching
 * their retention deadline (SPEC-0008 Events: {@code RetentionExpiring}), <strong>through the
 * Platform job governance</strong> (SPEC-0023 BR2/BR3; DL-0076). The business rule (which
 * documents, what horizon, what event) lives in the domain {@code
 * ComplianceService#flagRetentionExpiring}; this adapter only supplies the schedule. Running via
 * {@link GovernedJobs} adds the lock, the per-day idempotency window and the {@code JobRun}
 * history; the sweep itself re-publishes the same facts, so re-runs are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionExpiryScheduler {

  private final GovernedJobs governedJobs;

  /** Sweeps documents whose retention deadline is within the horizon. Interval configurable. */
  @Scheduled(
      initialDelayString = "${compliance.retention.initial-delay-ms:600000}",
      fixedDelayString = "${compliance.retention.sweep-interval-ms:86400000}")
  public void sweepExpiringRetention() {
    governedJobs.run("retention-expiry");
  }
}
