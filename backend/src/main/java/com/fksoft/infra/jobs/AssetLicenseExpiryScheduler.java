package com.fksoft.infra.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Assets module to flag expiring software
 * licenses (SPEC-0021 BR3; {@code AssetLicenseExpiring}; DL-0066), <strong>through the Platform job
 * governance</strong> (SPEC-0023 BR2/BR3; DL-0076). The business rule (which licenses, what
 * horizon, the idempotency) lives in {@code AssetService#flagExpiringLicenses}; this adapter only
 * supplies the schedule. Running via {@link GovernedJobs} adds the lock, the per-day idempotency
 * window and the {@code JobRun} history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetLicenseExpiryScheduler {

  private final GovernedJobs governedJobs;

  /** Sweeps active software licenses approaching expiry. Interval is configurable for ops/tests. */
  @Scheduled(
      initialDelayString = "${assets.license.initial-delay-ms:600000}",
      fixedDelayString = "${assets.license.sweep-interval-ms:86400000}")
  public void sweepExpiringLicenses() {
    governedJobs.run("asset-license-expiry");
  }
}
