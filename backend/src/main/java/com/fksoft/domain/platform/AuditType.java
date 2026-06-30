package com.fksoft.domain.platform;

/**
 * The kind of system-audit fact consolidated by the Platform (SPEC-0023 BR4). The audit is a
 * cross-cutting, append-only record of security/integration/job events — metadata only, never the
 * secret material (BR1). New producers register their own types through {@code
 * SystemAuditService.record(...)}.
 */
public enum AuditType {

  /** A governed job run started. */
  JOB_RUN_STARTED,

  /** A governed job run finished (SUCCEEDED | FAILED | SKIPPED). */
  JOB_RUN_FINISHED,

  /** A custodied certificate is approaching/past its validity end (BR5). */
  CERTIFICATE_EXPIRING,

  /** A certificate was custodied (security event). */
  CERTIFICATE_CUSTODIED,

  /** A generic security event surfaced by another module (e.g. an access/auth fact). */
  SECURITY_EVENT,

  /**
   * A successful login (Identity/SPEC-0024 access audit, DL-0083) — never carries a token/secret.
   */
  AUTH_LOGIN,

  /** An access denial — insufficient role for a sensitive action (Identity/SPEC-0024, DL-0083). */
  ACCESS_DENIED,

  /** A generic integration event surfaced by an adapter. */
  INTEGRATION_EVENT
}
