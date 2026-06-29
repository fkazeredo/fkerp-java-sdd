package com.fksoft.domain.marketing;

/**
 * The purpose a consent is granted/revoked for (SPEC-0019 BR1). Consent is
 * <strong>specific</strong> to a purpose (LGPD): a grant for NEWSLETTER does not authorize any
 * other use. v1 ships the newsletter purpose; new purposes are additive enum values.
 */
public enum ConsentPurpose {

  /** Receiving the external marketing newsletter (the v1 purpose). */
  NEWSLETTER
}
