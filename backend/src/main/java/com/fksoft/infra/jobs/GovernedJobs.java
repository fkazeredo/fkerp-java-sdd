package com.fksoft.infra.jobs;

import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.assets.AssetService;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.platform.CertificateCustodyService;
import com.fksoft.domain.platform.JobNotFoundException;
import com.fksoft.domain.platform.JobOutcome;
import com.fksoft.domain.platform.JobRunView;
import com.fksoft.domain.platform.PlatformJobService;
import com.fksoft.domain.portfolio.PortfolioService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central coordinator that runs each catalog job through the Platform governance (SPEC-0023
 * BR2/BR3; DL-0076). It maps every job name to its work (the call the owner module already exposes)
 * and executes it via {@link PlatformJobService#runWithGovernance} — applying the lock, the {@code
 * (job, window)} idempotency and the {@link com.fksoft.domain.platform.internal.JobRun} history.
 * The job's LOGIC still lives in its owner module; this only adds governance and a single place the
 * schedulers and the manual-trigger endpoint share.
 *
 * <p>Sweeps that re-publish the same facts (retention, license, representation, certificate, SLA)
 * use a per-day window so a re-run on the same day is idempotently SKIPPED; the point-clock crawl
 * uses a per-month window matching its period (DL-0033).
 */
@Slf4j
@Component
public class GovernedJobs {

  private final PlatformJobService jobService;
  private final Clock clock;
  private final ComplianceService complianceService;
  private final int retentionHorizonDays;
  private final int certificateHorizonDays;
  private final Map<String, Supplier<JobOutcome>> work = new LinkedHashMap<>();

  public GovernedJobs(
      PlatformJobService jobService,
      Clock clock,
      AfterSalesService afterSalesService,
      AssetService assetService,
      PortfolioService portfolioService,
      ComplianceService complianceService,
      CertificateCustodyService certificateCustodyService,
      @Value("${compliance.retention.horizon-days:30}") int retentionHorizonDays,
      @Value("${platform.certificate.expiry.horizon-days:30}") int certificateHorizonDays) {
    this.jobService = jobService;
    this.clock = clock;
    this.complianceService = complianceService;
    this.retentionHorizonDays = retentionHorizonDays;
    this.certificateHorizonDays = certificateHorizonDays;
    work.put(
        "aftersales-sla-sweep",
        () -> JobOutcome.of(afterSalesService.markBreaches(clock.instant())));
    work.put(
        "asset-license-expiry",
        () -> JobOutcome.of(assetService.flagExpiringLicenses(clock.instant())));
    work.put(
        "representation-expiry",
        () -> JobOutcome.of(portfolioService.flagExpiringContracts(clock.instant())));
    work.put(
        "certificate-expiry",
        () ->
            JobOutcome.of(
                certificateCustodyService.flagExpiringCertificates(
                    clock.instant(), certificateHorizonDays)));
    work.put(
        "retention-expiry",
        () -> JobOutcome.of(complianceService.flagRetentionExpiring(retentionHorizonDays)));
  }

  /** The current day as the idempotency window for daily sweeps (UTC). */
  String dayWindow() {
    return clock.instant().atZone(ZoneOffset.UTC).toLocalDate().toString();
  }

  /** The current month as the idempotency window for the point-clock crawl (DL-0033). */
  String monthWindow() {
    return YearMonth.now(clock).toString();
  }

  /**
   * Runs a catalog job by name under governance with a per-day window (used by the schedulers and
   * the manual trigger for the simple sweeps).
   *
   * @param jobName the catalog job name
   * @return the recorded run view
   * @throws JobNotFoundException when the job has no registered work
   */
  public JobRunView run(String jobName) {
    Supplier<JobOutcome> supplier = work.get(jobName);
    if (supplier == null) {
      throw new JobNotFoundException(jobName);
    }
    return jobService.runWithGovernance(jobName, dayWindow(), supplier);
  }

  /**
   * Runs a job under governance with an explicit window and work (for adapter-specific jobs such as
   * the point-clock crawl, which carries the source/period).
   */
  public JobRunView run(String jobName, String window, Supplier<JobOutcome> supplier) {
    return jobService.runWithGovernance(jobName, window, supplier);
  }
}
