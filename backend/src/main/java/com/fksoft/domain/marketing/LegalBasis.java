package com.fksoft.domain.marketing;

/**
 * The LGPD legal basis recorded with a consent (SPEC-0019 BR1; Lei 13.709/2018 art. 7). For the
 * newsletter the basis is the subject's CONSENT; LEGITIMATE_INTEREST is modeled for completeness
 * (some B2B communications may rely on it) but a send still requires a {@code GRANTED} status
 * (BR2).
 */
public enum LegalBasis {

  /** The subject's explicit, specific, informed consent (LGPD art. 7, I). */
  CONSENT,

  /** Legitimate interest of the controller (LGPD art. 7, IX). */
  LEGITIMATE_INTEREST
}
