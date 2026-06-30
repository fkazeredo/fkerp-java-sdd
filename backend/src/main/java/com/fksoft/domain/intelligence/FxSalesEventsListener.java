package com.fksoft.domain.intelligence;

import com.fksoft.domain.booking.BookingConfirmed;
import com.fksoft.domain.exchange.FxPositionClosed;
import com.fksoft.domain.exchange.RateSubsidyAccrued;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, read-only consumer of the FX/sales events that feed the {@code PromoFxAdvisor}
 * (SPEC-0013 Events, DL-0034). It consumes only the EXPOSED event types of the producing modules
 * ({@code booking}, {@code exchange}) and forwards them to {@link IntelligenceService}, which
 * writes only Intelligence's own read-models. It NEVER calls back into a producer — intelligence is
 * a consumer-leaf that advises, never commands (BR2).
 */
@Component
@RequiredArgsConstructor
class FxSalesEventsListener {

  private final IntelligenceService intelligenceService;

  @EventListener
  void onBookingConfirmed(BookingConfirmed event) {
    intelligenceService.onBookingConfirmed(event.bookingId(), event.accountId());
  }

  @EventListener
  void onRateSubsidyAccrued(RateSubsidyAccrued event) {
    intelligenceService.onRateSubsidyAccrued(event.bookingId(), event.subsidy());
  }

  @EventListener
  void onFxPositionClosed(FxPositionClosed event) {
    intelligenceService.onFxPositionClosed(event.bookingId(), event.totalGap());
  }
}
