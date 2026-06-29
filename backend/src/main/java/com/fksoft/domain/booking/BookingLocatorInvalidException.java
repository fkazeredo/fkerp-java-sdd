package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a locator is missing a code (an EXTERNAL locator must be non-empty — BR3). The
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class BookingLocatorInvalidException extends DomainException {

  public BookingLocatorInvalidException() {
    super("booking.locator.invalid");
  }
}
