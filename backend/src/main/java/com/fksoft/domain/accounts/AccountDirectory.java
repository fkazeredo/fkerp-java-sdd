package com.fksoft.domain.accounts;

import java.util.Optional;
import java.util.UUID;

/**
 * Public cross-module port of the accounts module: lets other modules (e.g. Quoting, SPEC-0005;
 * Sourcing, SPEC-0009) check whether an account exists or resolve it by document
 * <strong>without</strong> reaching into this module's repository or entities (Spring Modulith).
 * This is the only sanctioned way for another module to depend on accounts.
 */
public interface AccountDirectory {

  /**
   * Whether a registered account exists for the given id.
   *
   * @param accountId the account id to check
   * @return {@code true} if an account with that id exists
   */
  boolean exists(UUID accountId);

  /**
   * Resolves an account id by its document (SPEC-0009, DL-0017): the inbound ACL receives only the
   * document and must map it to a registered account. The document is normalized to digits before
   * matching.
   *
   * @param document the document as received (punctuation allowed)
   * @return the account id when a registered account matches the document, otherwise empty
   */
  Optional<UUID> findIdByDocument(String document);
}
