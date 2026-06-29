package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when creating a booking from a quote that does not exist (BR1), checked via the Quoting
 * facade. The presentation layer maps it to {@code 404 Not Found}.
 */
public class BookingQuoteNotFoundException extends DomainException {

  public BookingQuoteNotFoundException() {
    super("booking.quote.not-found");
  }
}
