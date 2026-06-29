package com.fksoft.domain.booking;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a lifecycle transition is not allowed by the state machine (BR2). The presentation
 * layer maps it to {@code 409 Conflict}.
 */
public class BookingTransitionInvalidException extends DomainException {

  public BookingTransitionInvalidException() {
    super("booking.transition.invalid");
  }
}
