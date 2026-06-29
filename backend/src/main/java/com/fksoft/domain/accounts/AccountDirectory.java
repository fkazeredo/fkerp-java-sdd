package com.fksoft.domain.accounts;

import java.util.UUID;

/**
 * Public cross-module port of the accounts module: lets other modules (e.g. Quoting, SPEC-0005)
 * check whether an account exists <strong>without</strong> reaching into this module's repository
 * or entities (Spring Modulith). This is the only sanctioned way for another module to depend on
 * accounts.
 */
public interface AccountDirectory {

  /**
   * Whether a registered account exists for the given id.
   *
   * @param accountId the account id to check
   * @return {@code true} if an account with that id exists
   */
  boolean exists(UUID accountId);
}
