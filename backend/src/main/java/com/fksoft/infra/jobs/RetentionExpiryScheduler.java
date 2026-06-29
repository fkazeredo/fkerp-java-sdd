package com.fksoft.infra.jobs;

import com.fksoft.domain.compliance.ComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Compliance to flag documents approaching
 * their retention deadline (SPEC-0008 Events: {@code RetentionExpiring}; redesign 8.2-H, vault
 * hygiene). The business rule (which documents, what horizon, what event) lives in the domain
 * {@link ComplianceService}; this adapter only supplies the schedule and the horizon. Idempotent:
 * it is a read that re-publishes the same facts, so re-runs are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionExpiryScheduler {

  private final ComplianceService complianceService;

  /** How many days ahead of the deadline a document is flagged as expiring (default 30). */
  @Value("${compliance.retention.horizon-days:30}")
  private int horizonDays;

  /** Sweeps documents whose retention deadline is within the horizon. Interval configurable. */
  @Scheduled(
      initialDelayString = "${compliance.retention.initial-delay-ms:600000}",
      fixedDelayString = "${compliance.retention.sweep-interval-ms:86400000}")
  public void sweepExpiringRetention() {
    int flagged = complianceService.flagRetentionExpiring(horizonDays);
    if (flagged > 0) {
      log.info(
          "Retention sweep flagged {} document(s) expiring within {} days", flagged, horizonDays);
    }
  }
}
