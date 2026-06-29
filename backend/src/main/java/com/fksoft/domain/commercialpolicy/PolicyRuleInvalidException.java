package com.fksoft.domain.commercialpolicy;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a rule definition is malformed (SPEC-0014 Validation Rules): bad effectivity ({@code
 * validFrom > validUntil}), a value that does not parse for its type, or a malformed key. The
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class PolicyRuleInvalidException extends DomainException {

  public PolicyRuleInvalidException() {
    super("policy.rule.invalid");
  }
}
