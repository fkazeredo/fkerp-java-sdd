package com.fksoft.domain.marketing;

/**
 * A consent/marketing subject referenced <strong>by value</strong> (SPEC-0019 BR1; modules-and-apis
 * "another context's id is a value, never an FK"): the subject's id (the Accounts/agent id as a
 * string) and its {@link SubjectType}. Two refs are equal when both fields match — the natural key
 * of a consent decision together with the purpose.
 *
 * @param id the subject id (value; the Accounts/agent identifier)
 * @param type the subject kind
 */
public record SubjectRef(String id, SubjectType type) {

  public SubjectRef {
    if (id == null || id.isBlank()) {
      throw new ConsentInvalidException();
    }
    if (type == null) {
      throw new ConsentInvalidException();
    }
    id = id.trim();
  }
}
