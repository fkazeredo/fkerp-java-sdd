package com.fksoft.domain.marketing;

/**
 * A consent/marketing subject referenced <strong>by value</strong> (SPEC-0019 BR1; modules-and-apis
 * "another context's id is a value, never an FK"): the subject's id (the Accounts/agent id as a
 * string) and its subject-type cadastro code (was {@code SubjectType}; SPEC-0031/DL-0116). Two refs
 * are equal when both fields match — the natural key of a consent decision together with the
 * purpose. The subject-type code is validated against the cadastro at the service boundary, not
 * here (this is a value object with no dependency on the validator port).
 *
 * @param id the subject id (value; the Accounts/agent identifier)
 * @param type the subject-type cadastro code
 */
public record SubjectRef(String id, String type) {

  public SubjectRef {
    if (id == null || id.isBlank()) {
      throw new ConsentInvalidException();
    }
    if (type == null || type.isBlank()) {
      throw new ConsentInvalidException();
    }
    id = id.trim();
    type = type.trim();
  }
}
