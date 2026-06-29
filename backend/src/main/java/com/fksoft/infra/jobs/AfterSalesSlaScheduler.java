package com.fksoft.infra.jobs;

import com.fksoft.domain.aftersales.AfterSalesService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the AfterSales module to flag SLA breaches
 * (SPEC-0018 BR4; DL-0053). The business rule (which cases, what deadline, what event) lives in the
 * domain {@link AfterSalesService#markBreaches}; this adapter only supplies the schedule and the
 * evaluation instant (the injected {@link Clock}), so the rule stays testable with a controlled
 * clock. Idempotent: a case already flagged is skipped, so re-runs are harmless. It is an
 * <strong>alert</strong> sweep — it never blocks the operation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSalesSlaScheduler {

  private final AfterSalesService afterSalesService;
  private final Clock clock;

  /**
   * Sweeps after-sales cases whose SLA deadline has passed. Interval configurable for ops/tests.
   */
  @Scheduled(
      initialDelayString = "${aftersales.sla.initial-delay-ms:600000}",
      fixedDelayString = "${aftersales.sla.sweep-interval-ms:900000}")
  public void sweepSlaBreaches() {
    int breached = afterSalesService.markBreaches(clock.instant());
    if (breached > 0) {
      log.info("SLA sweep flagged {} after-sales case(s) as breached", breached);
    }
  }
}
