package com.fksoft.domain.marketing;

/**
 * Command to record a consent grant (SPEC-0019 BR1): which subject, for which purpose, on which
 * legal basis, and the free-text source of the consent (e.g. {@code signup-form}). The grant is
 * appended as a new immutable row (DL-0056).
 *
 * @param subject the subject (value)
 * @param purpose the purpose being consented to
 * @param legalBasis the LGPD legal basis
 * @param source where the consent came from (audit; may contain no PII beyond a form name)
 */
public record GrantConsentCommand(
    SubjectRef subject, ConsentPurpose purpose, LegalBasis legalBasis, String source) {

  public GrantConsentCommand {
    if (subject == null || purpose == null || legalBasis == null) {
      throw new ConsentInvalidException();
    }
  }
}
