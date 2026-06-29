package com.fksoft.domain.error;

import java.time.Duration;

/**
 * Optional contract a {@link DomainException} may implement to signal that the caller should retry
 * after a given duration. The presentation layer maps this to a {@code Retry-After} response
 * header. This is domain data (how long to wait), not transport classification.
 */
public interface RateLimited {

  /** How long the caller should wait before retrying. */
  Duration retryAfter();
}
