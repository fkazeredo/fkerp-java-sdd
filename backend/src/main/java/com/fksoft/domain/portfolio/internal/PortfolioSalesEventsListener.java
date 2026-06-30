package com.fksoft.domain.portfolio.internal;

import com.fksoft.domain.booking.BookingConfirmed;
import com.fksoft.domain.portfolio.PortfolioService;
import com.fksoft.domain.reconciliation.ReconciliationCaseOpened;
import com.fksoft.domain.reconciliation.SpreadRealized;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal consumer of the sales events Portfolio projects the realized-vs-goal from
 * (SPEC-0020 BR4; DL-0062). It consumes only the EXPOSED events of the producing modules — never
 * their facades nor internals — so the module graph stays acyclic (Booking/Reconciliation do not
 * depend on Portfolio):
 *
 * <ul>
 *   <li>{@link ReconciliationCaseOpened} → links the case to its booking's attribution, so a later
 *       {@code SpreadRealized} (which carries only the caseId) can be resolved to the brand;
 *   <li>{@link BookingConfirmed} → adds a VOLUME contribution for the booking's attributed brand;
 *   <li>{@link SpreadRealized} → adds the realized spread (BRL) as a REVENUE contribution for the
 *       case's attributed brand.
 * </ul>
 *
 * <p>All projections are idempotent and do nothing for a sale with no pre-registered brand
 * attribution (no forced attribution — DL-0062). Named distinctly to avoid a Spring bean-name
 * collision with other modules' Booking/Reconciliation consumers.
 */
@Component
@RequiredArgsConstructor
class PortfolioSalesEventsListener {

  private final PortfolioService portfolioService;

  @EventListener
  void onReconciliationCaseOpened(ReconciliationCaseOpened event) {
    portfolioService.linkReconciliationCase(event.caseId(), event.bookingId());
  }

  @EventListener
  void onBookingConfirmed(BookingConfirmed event) {
    portfolioService.recordSaleVolume(event.bookingId(), event.occurredAt());
  }

  @EventListener
  void onSpreadRealized(SpreadRealized event) {
    portfolioService.recordSaleRevenue(event.caseId(), event.realizedSpread(), event.occurredAt());
  }
}
