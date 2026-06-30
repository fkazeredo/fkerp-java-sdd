/**
 * Platform module (SPEC-0023; OVERVIEW Part 6): the TI/operated-infra bounded context. It is
 * <strong>not</strong> a business-domain module — it orchestrates, guards and audits (BR6, enforced
 * by ArchUnit: Platform must not import another business module's domain). It owns three operated
 * capabilities (DL-0073):
 *
 * <p><strong>Certificate custody</strong> ({@link
 * com.fksoft.domain.platform.internal.PlatformCertificate}, DL-0074): the e-CNPJ (ICP-Brasil)
 * certificate and credentials are custodied with the secret material <strong>encrypted at
 * rest</strong> (AES-256-GCM envelope, master key outside the database) via the {@link
 * com.fksoft.domain.platform.SecretCipher} port; only metadata (subject, holder, fingerprint,
 * validity, status) is ever exposed — the private key/password NEVER appears in code, log, error,
 * event or DTO (BR1, security.md). Signing is the {@link
 * com.fksoft.domain.platform.CertificateSigner} port (DL-0078, graduates the Billing stub): it
 * signs without the material leaving custody. {@link
 * com.fksoft.domain.platform.CertificateExpiring} alerts the governance when validity nears its end
 * (BR5).
 *
 * <p><strong>Job governance</strong> ({@link
 * com.fksoft.domain.platform.internal.ScheduledJob}/{@link
 * com.fksoft.domain.platform.internal.JobRun}, DL-0075/DL-0076): every important job runs through
 * the registry — idempotency by {@code (job_name, window)}, distributed locking (a Postgres
 * advisory lock via the {@link com.fksoft.domain.platform.JobLock} port), and a {@link
 * com.fksoft.domain.platform.internal.JobRun} with start/finish/status/items/correlation id (BR2).
 * A failed job is recorded {@code FAILED}, never masked as success (BR3). The job's LOGIC stays in
 * its owner module; Platform only governs.
 *
 * <p><strong>System audit</strong> ({@link com.fksoft.domain.platform.internal.SystemAuditEntry},
 * DL-0077): an append-only consolidation of security/integration/job events with timestamp, actor
 * and correlation id (BR4) — metadata only, never secret material.
 *
 * <p>Spring Modulith application module. It is a consumer-leaf in the command graph: schedulers
 * ({@code infra.jobs}) drive it and it reacts to exposed events for the audit, so it forms no
 * dependency cycle. The {@code internal} sub-package (aggregates and repositories) is
 * module-private; the technical adapters (the AES-GCM cipher, the advisory lock, the signer) live
 * in {@code com.fksoft.infra.platform} behind the module's ports (ADR 0010).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Platform")
package com.fksoft.domain.platform;
