/**
 * Reconciliation module (SPEC-0007): closes the economic loop of the core. For each confirmed sale
 * it crosses what is expected to receive/pay, expected vs realized commission, and the FX
 * gain/loss, answering with a number whether the sale became margin or vanity (redesign 7.5).
 *
 * <p>Spring Modulith application module. It opens a case when it consumes {@code BookingConfirmed}
 * (copying the frozen provenance from the Quoting facade), cancels it on {@code BookingCancelled},
 * and records the realized settlement. It is read/derivation over facts and MUST NOT alter
 * Booking/Quote/Commissioning (BR8). Public API: {@link
 * com.fksoft.domain.reconciliation.ReconciliationService}, views, events and exceptions; the {@code
 * internal} sub-package (entity, repository, the booking-event listener) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reconciliation")
package com.fksoft.domain.reconciliation;
