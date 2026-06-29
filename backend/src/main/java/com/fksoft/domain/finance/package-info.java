/**
 * Finance module (SPEC-0015): the minimal AP/AR ledger and the accounting-period state machine with
 * monthly close — the seam where the Compliance veto (a financial entry missing its mandatory
 * document) actually prevents the month from closing (redesign 7.7; ROADMAP "co-delivers in Slice
 * 2"). This is the operational cash book, not a full accounting ERP (DL-0014); if full accounting
 * is required, this module becomes an adapter to a purchased ledger.
 *
 * <p>Spring Modulith application module. Types in this base package are the module's public API:
 * the {@link com.fksoft.domain.finance.FinanceService} use cases, the {@link
 * com.fksoft.domain.finance.LedgerDirectory} read port (consumed by Compliance), the {@link
 * com.fksoft.domain.finance.CloseGuard} port (consumed here, implemented by Compliance), value
 * objects, views, the {@link com.fksoft.domain.finance.LedgerEntryRegistered}/{@link
 * com.fksoft.domain.finance.PeriodClosed} events and the business exceptions. The {@code internal}
 * sub-package (entities, repositories) is module-private (Spring Modulith verify).
 *
 * <p>Event-driven AP/AR posting (SPEC-0015 BR5, DL-0041): Finance is a <strong>leaf
 * consumer</strong> of the Booking module's charge events ({@code CancellationCharged}, {@code
 * NoShowCharged}, {@code MerchantObligationIncurred}), turning them into ledger entries
 * idempotently (state-check + UNIQUE on {@code (bookingId, chargeKind)}). It reads only those
 * EXPOSED event types and never calls back into Booking, so the dependency {@code finance →
 * booking} stays acyclic (Booking depends on neither Finance nor Compliance; Compliance depends on
 * Finance).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Finance")
package com.fksoft.domain.finance;
