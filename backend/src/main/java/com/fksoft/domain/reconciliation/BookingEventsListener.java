package com.fksoft.domain.reconciliation;

import com.fksoft.domain.booking.BookingCancelled;
import com.fksoft.domain.booking.BookingConfirmed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal in-process consumer of Booking lifecycle events. Runs synchronously within the
 * booking transition's transaction, so a case is opened/cancelled atomically with the booking
 * change. Reconciliation writes only its own table (it never alters Booking — BR8). Opening is
 * idempotent per booking (BR1: unique {@code booking_id} + existence pre-check).
 */
@Component
@RequiredArgsConstructor
class BookingEventsListener {

  private final ReconciliationService reconciliationService;

  @EventListener
  void onBookingConfirmed(BookingConfirmed event) {
    reconciliationService.openCase(event.bookingId(), event.quoteId());
  }

  @EventListener
  void onBookingCancelled(BookingCancelled event) {
    reconciliationService.cancelCase(event.bookingId());
  }
}
