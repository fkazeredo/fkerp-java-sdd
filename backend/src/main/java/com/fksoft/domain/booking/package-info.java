/**
 * Booking module (SPEC-0006): turns an accepted quote into an operational commitment with an
 * explicit lifecycle and a locator, and emits the lifecycle events that drive commission accrual
 * and reconciliation. The same Booking is the service to deliver (Operations) and the obligation to
 * settle (Finance).
 *
 * <p>SPEC-0010 graduates the simple cancellation into a rich <strong>cancellation policy as an
 * object</strong> ({@link com.fksoft.domain.booking.CancellationPolicy} with penalty windows, the
 * cancellation-type behavior kept in {@link com.fksoft.domain.booking.CancellationTypeCodes},
 * {@link com.fksoft.domain.booking.NoShowPolicy}) frozen onto the booking at confirmation, the
 * resulting {@link com.fksoft.domain.booking.Charge}s (distinct facts that never net out — the
 * merchant trap), and the policy source administered through {@link
 * com.fksoft.domain.booking.CancellationPolicyAdminService}. Per DL-0020 this lives in the booking
 * module (no speculative {@code cancellation}/{@code policy} module).
 *
 * <p>Spring Modulith application module. Public API: {@link
 * com.fksoft.domain.booking.BookingService}, {@link
 * com.fksoft.domain.booking.CancellationPolicyAdminService}, the lifecycle value types ({@link
 * com.fksoft.domain.booking.BookingStatus} state machine, {@link
 * com.fksoft.domain.booking.Locator}), the cancellation value types, views, the {@code
 * BookingConfirmed}/{@code BookingCancelled}/{@code BookingNoShow}/{@code
 * CancellationCharged}/{@code MerchantObligationIncurred}/{@code NoShowCharged} events and the
 * business exceptions. It validates the quote through the Quoting facade ({@code QuoteDirectory}).
 * The implementation types (aggregates, repositories, the penalty-windows codec) live in this same
 * package marked {@link com.fksoft.domain.ModuleInternal} and must never be reached from other
 * modules — encapsulation is enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Booking")
package com.fksoft.domain.booking;
