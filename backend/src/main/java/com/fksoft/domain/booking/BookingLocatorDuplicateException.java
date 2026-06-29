package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a locator {@code (origin, code)} already exists (BR3). Translated from the
 * unique-index violation; the presentation layer maps it to {@code 409 Conflict}.
 */
public class BookingLocatorDuplicateException extends DomainException {

  public BookingLocatorDuplicateException() {
    super("booking.locator.duplicate");
  }
}
