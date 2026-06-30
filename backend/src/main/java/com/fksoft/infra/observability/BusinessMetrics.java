package com.fksoft.infra.observability;

import com.fksoft.domain.billing.CommissionInvoiceIssued;
import com.fksoft.domain.booking.BookingCancelled;
import com.fksoft.domain.booking.BookingConfirmed;
import com.fksoft.domain.finance.PeriodClosed;
import com.fksoft.domain.identity.UserAuthenticated;
import com.fksoft.domain.quoting.PriceOverridden;
import com.fksoft.domain.quoting.QuoteComposed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Business metrics over the domain events the prior phases already publish (SPEC-0027/DL-0098).
 * This is an <strong>infra</strong> consumer: it depends on Micrometer and on the modules' exposed
 * events, so the pure {@code domain} never imports Micrometer/Actuator (ADR 0012 — proven by an
 * ArchUnit rule). It mirrors the existing {@code PlatformAuditListener} pattern (an in-process
 * event consumer), turning each business fact into a Micrometer {@link Counter} that the Prometheus
 * registry exports (e.g. {@code acme.bookings.confirmed} → {@code acme_bookings_confirmed_total}).
 *
 * <p>The instrumentation is a passive side effect (BR7): it never changes behavior, and a metrics
 * failure is swallowed (logged) so it can never fail a business operation. The counters are
 * pre-registered at construction so they appear in the scrape from the first request, even before
 * the first event. No personal data or secret is ever used as a tag.
 */
@Slf4j
@Component
public class BusinessMetrics {

  private final Counter bookingsConfirmed;
  private final Counter bookingsCancelled;
  private final Counter quotesComposed;
  private final Counter quotesOverridden;
  private final Counter invoicesIssued;
  private final Counter periodsClosed;
  private final Counter logins;

  public BusinessMetrics(MeterRegistry registry) {
    this.bookingsConfirmed =
        Counter.builder("acme.bookings.confirmed")
            .description("Bookings confirmed (BookingConfirmed)")
            .register(registry);
    this.bookingsCancelled =
        Counter.builder("acme.bookings.cancelled")
            .description("Bookings cancelled (BookingCancelled)")
            .register(registry);
    this.quotesComposed =
        Counter.builder("acme.quotes.composed")
            .description("Quotes composed (QuoteComposed)")
            .register(registry);
    this.quotesOverridden =
        Counter.builder("acme.quotes.overridden")
            .description("Price overrides recorded (PriceOverridden)")
            .register(registry);
    this.invoicesIssued =
        Counter.builder("acme.billing.invoices.issued")
            .description("Commission invoices issued (CommissionInvoiceIssued)")
            .register(registry);
    this.periodsClosed =
        Counter.builder("acme.finance.periods.closed")
            .description("Accounting periods closed (PeriodClosed)")
            .register(registry);
    this.logins =
        Counter.builder("acme.identity.logins")
            .description("Successful authentications (UserAuthenticated)")
            .register(registry);
  }

  @EventListener
  void onBookingConfirmed(BookingConfirmed event) {
    safelyIncrement(bookingsConfirmed);
  }

  @EventListener
  void onBookingCancelled(BookingCancelled event) {
    safelyIncrement(bookingsCancelled);
  }

  @EventListener
  void onQuoteComposed(QuoteComposed event) {
    safelyIncrement(quotesComposed);
  }

  @EventListener
  void onPriceOverridden(PriceOverridden event) {
    safelyIncrement(quotesOverridden);
  }

  @EventListener
  void onCommissionInvoiceIssued(CommissionInvoiceIssued event) {
    safelyIncrement(invoicesIssued);
  }

  @EventListener
  void onPeriodClosed(PeriodClosed event) {
    safelyIncrement(periodsClosed);
  }

  @EventListener
  void onUserAuthenticated(UserAuthenticated event) {
    safelyIncrement(logins);
  }

  /**
   * A metrics failure must never break the business flow (BR7) — count, log on failure, move on.
   */
  private void safelyIncrement(Counter counter) {
    try {
      counter.increment();
    } catch (RuntimeException ex) {
      log.warn("Failed to record business metric {}", counter.getId().getName(), ex);
    }
  }
}
