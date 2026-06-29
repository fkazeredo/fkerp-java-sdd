/**
 * Booking module (SPEC-0006): turns an accepted quote into an operational commitment with an
 * explicit lifecycle and a locator, and emits the lifecycle events that drive commission accrual
 * and reconciliation. The same Booking is the service to deliver (Operations) and the obligation to
 * settle (Finance).
 *
 * <p>Spring Modulith application module. Public API: {@link
 * com.fksoft.domain.booking.BookingService}, the lifecycle value types ({@link
 * com.fksoft.domain.booking.BookingStatus} state machine, {@link
 * com.fksoft.domain.booking.Locator}), views, the {@code BookingConfirmed}/{@code
 * BookingCancelled}/{@code BookingNoShow} events and the business exceptions. It validates the
 * quote through the Quoting facade ({@code QuoteDirectory}); the {@code internal} sub-package is
 * module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Booking")
package com.fksoft.domain.booking;
