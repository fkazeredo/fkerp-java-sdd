package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.SubjectRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/marketing/consents} (SPEC-0019 BR1). The {@code subject} is a
 * value (id + subject-type code); the purpose and legal basis are required; the source is the
 * free-text origin. The {@code purpose} and subject {@code type} are cadastro codes (was {@code
 * ConsentPurpose}/{@code SubjectType}; SPEC-0031/DL-0116) — the wire stays a string, validated
 * against the cadastro by the service.
 *
 * @param subject the subject (id + subject-type code)
 * @param purpose the purpose cadastro code being consented to
 * @param legalBasis the LGPD legal basis (defaults to CONSENT when omitted)
 * @param source the free-text origin of the consent (e.g. {@code signup-form})
 */
public record GrantConsentRequest(
    @NotNull Subject subject, @NotBlank String purpose, LegalBasis legalBasis, String source) {

  /**
   * The subject reference in the request.
   *
   * @param id the subject id (value)
   * @param type the subject-type cadastro code
   */
  public record Subject(@NotNull String id, @NotBlank String type) {}

  /** Translates this request to the domain command (legal basis defaults to CONSENT). */
  public GrantConsentCommand toCommand() {
    return new GrantConsentCommand(
        new SubjectRef(subject.id(), subject.type()),
        purpose,
        legalBasis == null ? LegalBasis.CONSENT : legalBasis,
        source);
  }
}
