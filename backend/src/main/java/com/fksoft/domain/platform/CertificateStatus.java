package com.fksoft.domain.platform;

/**
 * Lifecycle status of a custodied e-CNPJ certificate (SPEC-0023 BR5). Derived from the validity
 * dates against the evaluation instant, except {@link #REVOKED} which is an explicit decision.
 */
public enum CertificateStatus {

  /** Valid and not within the expiry-warning horizon. */
  VALID,

  /** Still valid but within the expiry-warning horizon (BR5 — alert raised). */
  EXPIRING,

  /** Past its validity end date. */
  EXPIRED,

  /** Explicitly revoked (no longer usable regardless of dates). */
  REVOKED
}
