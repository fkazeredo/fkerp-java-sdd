package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.ConsentPurpose;
import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.domain.marketing.SubjectType;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/marketing/consents} (SPEC-0019 BR1). The {@code subject} is a
 * value (id + type); the purpose and legal basis are required; the source is the free-text origin.
 *
 * @param subject the subject (id + type)
 * @param purpose the purpose being consented to
 * @param legalBasis the LGPD legal basis (defaults to CONSENT when omitted)
 * @param source the free-text origin of the consent (e.g. {@code signup-form})
 */
public record GrantConsentRequest(
    @NotNull Subject subject,
    @NotNull ConsentPurpose purpose,
    LegalBasis legalBasis,
    String source) {

  /**
   * The subject reference in the request.
   *
   * @param id the subject id (value)
   * @param type the subject kind
   */
  public record Subject(@NotNull String id, @NotNull SubjectType type) {}

  /** Translates this request to the domain command (legal basis defaults to CONSENT). */
  public GrantConsentCommand toCommand() {
    return new GrantConsentCommand(
        new SubjectRef(subject.id(), subject.type()),
        purpose,
        legalBasis == null ? LegalBasis.CONSENT : legalBasis,
        source);
  }
}
