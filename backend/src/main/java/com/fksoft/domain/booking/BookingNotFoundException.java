package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a booking is looked up or transitioned by an id that does not exist. The presentation
 * layer maps it to {@code 404 Not Found}.
 */
public class BookingNotFoundException extends DomainException {

  public BookingNotFoundException() {
    super("booking.not-found");
  }
}
