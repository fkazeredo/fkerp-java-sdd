package com.fksoft.domain.quoting;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Public cross-module port of the quoting module for the INTEGRATED branch (SPEC-0009): lets the
 * Sourcing ACL create a Quote from a <strong>trusted, closed external price</strong> without
 * running the suggestion engine and without any override (BR2). Only domain values cross this
 * boundary — the external vendor DTO never reaches the domain (BR6).
 */
public interface QuoteIntegrationPort {

  /**
   * Creates an INTEGRATED quote: {@code suggestedAmount == appliedAmount == externalPrice}; no
   * commission/markup/FX is computed and no {@code OverrideRecord} is created (BR2).
   *
   * @param accountId the resolved account the quote is for
   * @param sourceOfferId the sourced offer that records the provenance (nullable)
   * @param externalPrice the trusted, closed external price
   * @param validUntil optional validity instant
   * @param actor who/what created it (audit; e.g. the connector)
   * @return the id of the created quote
   */
  UUID createIntegratedQuote(
      UUID accountId, UUID sourceOfferId, Money externalPrice, Instant validUntil, String actor);
}
