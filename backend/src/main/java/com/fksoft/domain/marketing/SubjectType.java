package com.fksoft.domain.marketing;

/**
 * The kind of consent/marketing subject (SPEC-0019 BR1). The base is B2B — agencies/agents — so the
 * subject is a commercial entity referenced <strong>by value</strong> (its id is a string, never a
 * cross-context FK to Accounts).
 */
public enum SubjectType {

  /** A commercial account (agency). */
  ACCOUNT,

  /** An individual agent. */
  AGENT
}
