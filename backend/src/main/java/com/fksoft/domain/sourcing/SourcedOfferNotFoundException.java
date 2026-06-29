package com.fksoft.domain.sourcing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a sourced offer is requested by an id that does not exist. The presentation layer
 * maps it to {@code 404 Not Found}.
 */
public class SourcedOfferNotFoundException extends DomainException {

  public SourcedOfferNotFoundException() {
    super("sourcing.offer.not-found");
  }
}
