package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.SubjectRef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/marketing/erasure} (SPEC-0019 BR6): the subject whose marketing
 * data should be erased (PII removed; revocation tombstone preserved — DL-0058). The {@code
 * subjectType} is a cadastro code (was {@code SubjectType}; SPEC-0031/DL-0116).
 *
 * @param subjectId the subject id (value)
 * @param subjectType the subject-type cadastro code
 */
public record ErasureRequest(@NotNull String subjectId, @NotBlank String subjectType) {

  /** Translates this request to the domain subject reference. */
  public SubjectRef toSubject() {
    return new SubjectRef(subjectId, subjectType);
  }
}
