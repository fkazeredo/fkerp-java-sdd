package com.fksoft.infra.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically asks the Platform to flag e-CNPJ certificates
 * approaching their validity end (SPEC-0023 BR5; DL-0076), <strong>through the Platform job
 * governance</strong> (BR2/BR3). The business rule (which certificates, what horizon, the
 * idempotency) lives in {@code CertificateCustodyService#flagExpiringCertificates}; this adapter
 * only supplies the schedule. Running via {@link GovernedJobs} adds the lock, the per-day
 * idempotency window and the {@code JobRun} history; it is an <strong>alert</strong> — it never
 * blocks signing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateExpiryScheduler {

  private final GovernedJobs governedJobs;

  /** Sweeps custodied certificates within the expiry horizon. Interval configurable. */
  @Scheduled(
      initialDelayString = "${platform.certificate.expiry.initial-delay-ms:600000}",
      fixedDelayString = "${platform.certificate.expiry.sweep-interval-ms:86400000}")
  public void sweepExpiringCertificates() {
    governedJobs.run("certificate-expiry");
  }
}
