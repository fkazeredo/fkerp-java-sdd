package com.fksoft.domain.finance;

import org.springframework.stereotype.Component;

/**
 * Default {@link CloseGuard} used until the Compliance module provides the real veto (SPEC-0008).
 * This is a <strong>traceable seam</strong> (simulation-and-mocking.md): it is not fake business
 * logic — it makes Finance independently buildable and green. Once Compliance is present its
 * implementation is annotated {@code @Primary} and supersedes this one for injection, with no
 * change to Finance. Both beans coexist; {@code @Primary} resolves the choice deterministically.
 */
@Component
public class AlwaysAllowsCloseGuard implements CloseGuard {

  @Override
  public CloseDecision checkClose(AccountingPeriodId period) {
    return CloseDecision.allowed();
  }
}
