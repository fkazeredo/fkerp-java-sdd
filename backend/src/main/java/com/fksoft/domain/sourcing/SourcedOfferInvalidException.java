package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a {@link com.fksoft.domain.sourcing.internal.SourcedOffer} is registered with empty
 * product text (BR1: product text is a free-text, non-empty offer). The presentation layer maps it
 * to {@code 400 Bad Request}.
 */
public class SourcedOfferInvalidException extends DomainException {

  public SourcedOfferInvalidException() {
    super("sourcing.offer.invalid");
  }
}
