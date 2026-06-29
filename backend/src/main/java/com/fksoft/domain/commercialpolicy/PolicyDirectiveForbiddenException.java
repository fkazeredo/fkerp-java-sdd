package com.fksoft.domain.commercialpolicy;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a directive is issued without the director role, or a rule is defined without the
 * required curator/admin role (SPEC-0014 BR5/BR7, DL-0038). The presentation layer maps it to
 * {@code 403 Forbidden}. A directive is the "director's order" and must be authorized and audited.
 */
public class PolicyDirectiveForbiddenException extends DomainException {

  public PolicyDirectiveForbiddenException() {
    super("policy.directive.forbidden");
  }
}
