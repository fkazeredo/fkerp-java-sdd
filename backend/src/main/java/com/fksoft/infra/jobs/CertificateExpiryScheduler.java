package com.fksoft.infra.jobs;

import com.fksoft.domain.platform.CertificateCustodyService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Platform to flag e-CNPJ certificates
 * approaching their validity end (SPEC-0023 BR5; DL-0076). The business rule (which certificates,
 * what horizon, what event) lives in {@link CertificateCustodyService#flagExpiringCertificates};
 * this adapter only supplies the schedule and the evaluation instant (the injected {@link Clock}),
 * so the rule stays testable with a controlled clock. Idempotent: a certificate already alerted is
 * skipped, so re-runs are harmless. It is an <strong>alert</strong> — it never blocks signing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateExpiryScheduler {

  private final CertificateCustodyService custodyService;
  private final Clock clock;

  /** How many days ahead of validity end a certificate is flagged as expiring (default 30). */
  @Value("${platform.certificate.expiry.horizon-days:30}")
  private int horizonDays;

  /** Sweeps custodied certificates within the expiry horizon. Interval configurable. */
  @Scheduled(
      initialDelayString = "${platform.certificate.expiry.initial-delay-ms:600000}",
      fixedDelayString = "${platform.certificate.expiry.sweep-interval-ms:86400000}")
  public void sweepExpiringCertificates() {
    int flagged = custodyService.flagExpiringCertificates(clock.instant(), horizonDays);
    if (flagged > 0) {
      log.info("Certificate expiry sweep flagged {} certificate(s)", flagged);
    }
  }
}
