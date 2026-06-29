package com.fksoft.domain.quoting;

/**
 * Status of a quote. Phase 1 only needs {@link #COMPOSED} (the quote exists with its frozen
 * composition); the booking lifecycle is owned by SPEC-0006. External value is the name.
 */
public enum QuoteStatus {
  COMPOSED
}
