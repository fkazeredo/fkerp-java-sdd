package com.fksoft.domain.marketing;

import com.fksoft.domain.booking.BookingConfirmed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal consumer of the Booking {@link BookingConfirmed} event (SPEC-0019 BR5/DL-0057).
 * Marketing consumes only the EXPOSED event of the producing module — never the Booking facade nor
 * its internals — so the module graph stays acyclic (Booking does not depend on Marketing). When a
 * booking is confirmed, it asks {@link MarketingService} to confirm any pre-registered attribution
 * (which publishes {@code CampaignConverted}); a booking with no campaign code does nothing.
 *
 * <p>Named distinctly from Reconciliation's own {@code BookingEventsListener} to avoid a Spring
 * bean name collision (each module owns its own Booking-event consumer).
 */
@Component
@RequiredArgsConstructor
class MarketingBookingEventsListener {

  private final MarketingService marketingService;

  @EventListener
  void onBookingConfirmed(BookingConfirmed event) {
    marketingService.confirmConversion(event.bookingId());
  }
}
