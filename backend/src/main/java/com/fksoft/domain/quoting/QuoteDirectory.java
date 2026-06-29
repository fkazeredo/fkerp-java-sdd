package com.fksoft.domain.quoting;

import java.util.Optional;
import java.util.UUID;

/**
 * Public cross-module port of the quoting module: lets Booking (SPEC-0006) and Reconciliation
 * (SPEC-0007) read a quote's frozen snapshot without touching this module's repository or entities
 * (Spring Modulith).
 */
public interface QuoteDirectory {

  /**
   * The frozen snapshot of a quote, if it exists.
   *
   * @param quoteId the quote id
   * @return the snapshot, or empty when no quote has that id
   */
  Optional<QuoteSnapshot> find(UUID quoteId);
}
