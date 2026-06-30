package com.fksoft.infra.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Admin module to flag administrative
 * contracts approaching their validity end (SPEC-0025 BR5; {@code AdminContractExpiring}; DL-0087),
 * <strong>through the Platform job governance</strong> (SPEC-0023 BR2/BR3; DL-0076). The business
 * rule (which contracts, what horizon, the idempotency) lives in {@code
 * AdminService#flagExpiringContracts}; this adapter only supplies the schedule. Running via {@link
 * GovernedJobs} adds the lock, the per-day idempotency window and the {@code JobRun} history; it is
 * an <strong>alert</strong> — it never blocks anything. Before this adapter the flag was only
 * reachable via the {@code POST /api/admin/contracts/flag-expiring} endpoint; now the catalog job
 * is actually driven.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminContractExpiryScheduler {

  private final GovernedJobs governedJobs;

  /** Sweeps administrative contracts approaching expiry. Interval is configurable for ops/tests. */
  @Scheduled(
      initialDelayString = "${admin.contract.initial-delay-ms:600000}",
      fixedDelayString = "${admin.contract.sweep-interval-ms:86400000}")
  public void sweepExpiringContracts() {
    governedJobs.run("admin-contract-expiry");
  }
}
