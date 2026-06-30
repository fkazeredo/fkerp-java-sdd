package com.fksoft.domain.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal consumer that consolidates the Platform's own exposed events into the append-only
 * system audit (SPEC-0023 BR4; DL-0077). It records a metadata-only line for each job-run lifecycle
 * event and each certificate-expiry alert — <strong>never any secret material</strong> (BR1). Other
 * modules' security/integration facts reach the audit through the {@link SystemAuditService#record}
 * facade directly, so the Platform stays a consumer-leaf (it imports no other business module's
 * command facade — BR6).
 *
 * <p>The detail is a small, hand-built JSON of identifiers/status only; no PII or key material is
 * ever serialized here.
 */
@Component
@RequiredArgsConstructor
class PlatformAuditListener {

  private final SystemAuditService auditService;

  @EventListener
  void onJobRunStarted(JobRunStarted event) {
    auditService.record(
        AuditType.JOB_RUN_STARTED,
        "system",
        "{\"runId\":\"" + event.runId() + "\",\"job\":\"" + event.job() + "\"}");
  }

  @EventListener
  void onJobRunFinished(JobRunFinished event) {
    String detail =
        "{\"runId\":\""
            + event.runId()
            + "\",\"job\":\""
            + event.job()
            + "\",\"status\":\""
            + event.status()
            + "\",\"items\":"
            + event.items()
            + "}";
    auditService.record(AuditType.JOB_RUN_FINISHED, "system", detail);
  }

  @EventListener
  void onCertificateExpiring(CertificateExpiring event) {
    String detail =
        "{\"fingerprint\":\""
            + event.fingerprint()
            + "\",\"validUntil\":\""
            + event.validUntil()
            + "\",\"daysToExpiry\":"
            + event.daysToExpiry()
            + "}";
    auditService.record(AuditType.CERTIFICATE_EXPIRING, "system", detail);
  }
}
