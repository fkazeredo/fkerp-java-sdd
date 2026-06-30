package com.fksoft.infra.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the AfterSales module to flag SLA breaches
 * (SPEC-0018 BR4; DL-0053), <strong>through the Platform job governance</strong> (SPEC-0023
 * BR2/BR3; DL-0076). The business rule (which cases, what deadline, what event) lives in the domain
 * {@code AfterSalesService#markBreaches}; this adapter only supplies the schedule. Running via
 * {@link GovernedJobs} adds the lock, the per-day idempotency window and the {@code JobRun}
 * history; the sweep itself is an idempotent <strong>alert</strong> — it never blocks the
 * operation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSalesSlaScheduler {

  private final GovernedJobs governedJobs;

  /**
   * Sweeps after-sales cases whose SLA deadline has passed. Interval configurable for ops/tests.
   */
  @Scheduled(
      initialDelayString = "${aftersales.sla.initial-delay-ms:600000}",
      fixedDelayString = "${aftersales.sla.sweep-interval-ms:900000}")
  public void sweepSlaBreaches() {
    governedJobs.run("aftersales-sla-sweep");
  }
}
