package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.domain.marketing.SubjectType;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/marketing/erasure} (SPEC-0019 BR6): the subject whose marketing
 * data should be erased (PII removed; revocation tombstone preserved — DL-0058).
 *
 * @param subjectId the subject id (value)
 * @param subjectType the subject kind
 */
public record ErasureRequest(@NotNull String subjectId, @NotNull SubjectType subjectType) {

  /** Translates this request to the domain subject reference. */
  public SubjectRef toSubject() {
    return new SubjectRef(subjectId, subjectType);
  }
}
