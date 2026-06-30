package com.fksoft.infra.jobs;

import com.fksoft.domain.assets.AssetService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Assets module to flag expiring software
 * licenses and raise the governance alert (SPEC-0021 BR3; {@code AssetLicenseExpiring}; DL-0066).
 * The business rule (which licenses, what horizon, what event, the idempotency) lives in the domain
 * {@link AssetService#flagExpiringLicenses}; this adapter only supplies the schedule and the clock.
 * Idempotent: a license already signaled is left untouched, so re-runs are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetLicenseExpiryScheduler {

  private final AssetService assetService;
  private final Clock clock;

  /** Sweeps active software licenses approaching expiry. Interval is configurable for ops/tests. */
  @Scheduled(
      initialDelayString = "${assets.license.initial-delay-ms:600000}",
      fixedDelayString = "${assets.license.sweep-interval-ms:86400000}")
  public void sweepExpiringLicenses() {
    int flagged = assetService.flagExpiringLicenses(clock.instant());
    if (flagged > 0) {
      log.info("Asset license-expiry sweep flagged {} license(s) approaching expiry", flagged);
    }
  }
}
