package com.fksoft.domain.booking;

import java.util.Set;

/**
 * Booking lifecycle state machine (BR2). Each state knows the states it may transition to; an
 * invalid transition is rejected by the aggregate with a specific business exception. A new booking
 * starts {@link #ORDERED} (the {@link #QUOTED} stage belongs to Quoting). External value is the
 * constant name.
 */
public enum BookingStatus {
  QUOTED,
  ORDERED,
  PENDING,
  CONFIRMED,
  CHANGED,
  CANCELLED,
  NO_SHOW,
  COMPLETED;

  /** The states this status may transition to. */
  public Set<BookingStatus> allowedNext() {
    return switch (this) {
      case QUOTED -> Set.of(ORDERED);
      case ORDERED -> Set.of(PENDING);
      case PENDING -> Set.of(CONFIRMED, CANCELLED);
      case CONFIRMED -> Set.of(CHANGED, CANCELLED, NO_SHOW, COMPLETED);
      case CHANGED -> Set.of(CONFIRMED, CANCELLED);
      case CANCELLED, NO_SHOW, COMPLETED -> Set.of();
    };
  }

  /** Whether a transition from this status to {@code target} is allowed. */
  public boolean canTransitionTo(BookingStatus target) {
    return allowedNext().contains(target);
  }
}
