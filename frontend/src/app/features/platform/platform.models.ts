/** Lifecycle status of a job run (SPEC-0023). */
export type JobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';

/** Custody status of the e-CNPJ certificate (SPEC-0023 BR1). */
export type CertificateStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'REVOKED';

/** A system-audit entry type (SPEC-0023 BR4). */
export type AuditType =
  | 'JOB_RUN_STARTED'
  | 'JOB_RUN_FINISHED'
  | 'CERTIFICATE_EXPIRING'
  | 'CERTIFICATE_CUSTODIED'
  | 'SECURITY_EVENT'
  | 'AUTH_LOGIN'
  | 'ACCESS_DENIED'
  | 'INTEGRATION_EVENT'
  | 'ADMIN_CHANGE';

/** Read view of a governed scheduled job (SPEC-0023). */
export interface ScheduledJobView {
  name: string;
  cron: string;
  enabled: boolean;
  ownerModule: string;
  lastRunAt: string | null;
}

/** Read view of a recorded job run (SPEC-0023). */
export interface JobRunView {
  runId: string;
  job: string;
  startedAt: string;
  finishedAt: string | null;
  status: JobStatus;
  items: number | null;
  failureClass: string | null;
  correlationId: string;
}

/**
 * Read view of the e-CNPJ certificate — METADATA ONLY (SPEC-0023 BR1). The private key and password
 * are NEVER returned by any endpoint; this view carries no secret material.
 */
export interface CertificateView {
  subject: string;
  holderDocument: string;
  fingerprint: string;
  validFrom: string;
  validUntil: string;
  daysToExpiry: number;
  status: CertificateStatus;
}

/** Read view of a consolidated system-audit entry (SPEC-0023 BR4 — metadata only). */
export interface SystemAuditView {
  id: string;
  occurredAt: string;
  actor: string;
  type: AuditType;
  detail: string;
  correlationId: string;
}
