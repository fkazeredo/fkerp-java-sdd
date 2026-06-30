/**
 * AfterSales module (SPEC-0018): the post-sale context that registers support cases (chamados),
 * conducts changes and cancellations/refunds (delegating to the owners) and measures SLA —
 * producing the "cost to serve" the DSS uses to find the product/supplier that "sells nicely, loses
 * money" (redesign Part 8). The aggregate is {@link com.fksoft.domain.aftersales.SupportCase}: type
 * ∈ {COMPLAINT, CHANGE_REQUEST, CANCELLATION_REQUEST, REFUND_REQUEST, INFO}, the referenced booking
 * (value), the status machine OPEN→IN_PROGRESS→WAITING→RESOLVED→CLOSED (BR4), the governed SLA
 * deadlines (DL-0052), the breach alert flag (BR4/DL-0053) and the cost-to-serve (BR5/DL-0053).
 *
 * <p>Spring Modulith application module. It <strong>orchestrates, it does not reimplement</strong>:
 * the SLA deadlines are governed parameters resolved through {@link
 * com.fksoft.domain.commercialpolicy.CommercialPolicyService} (BR1/DL-0052); a {@code
 * CANCELLATION_REQUEST} resolved as cancel calls {@link com.fksoft.domain.booking.BookingService}
 * (which owns the SPEC-0010 penalty policy, BR2); a {@code REFUND_REQUEST} approved calls {@link
 * com.fksoft.domain.payout.PayoutService} as a {@code REFUND} referencing the case as its origin
 * obligation (BR3/DL-0054), idempotently and without ever cancelling the supplier obligation — the
 * merchant trap stays intact (DL-0024/DL-0051). AfterSales never changes the reservation state nor
 * posts financials itself (BR6).
 *
 * <p>It depends on the {@code commercialpolicy}/{@code booking}/{@code payout} facades and the
 * {@code money}/{@code error} kernels; none of those depend back on AfterSales, so the module graph
 * stays <strong>acyclic</strong> (Spring Modulith verify). Types in this base package are the
 * module's public API: the {@link com.fksoft.domain.aftersales.AfterSalesService} use cases, the
 * commands/ views/value objects ({@link com.fksoft.domain.aftersales.CostToServe}), the
 * enums-with-behavior ({@link com.fksoft.domain.aftersales.SupportCaseStatus}/{@link
 * com.fksoft.domain.aftersales.SupportCaseType}/{@link
 * com.fksoft.domain.aftersales.CaseResolution}), the events ({@link
 * com.fksoft.domain.aftersales.SupportCaseOpened}/{@link
 * com.fksoft.domain.aftersales.SupportCaseResolved}/{@link
 * com.fksoft.domain.aftersales.SlaBreached}) and the business exceptions. The implementation types
 * (the aggregate and its repository) live in this same package marked {@link
 * com.fksoft.domain.ModuleInternal} and must never be reached from other modules — encapsulation is
 * enforced by ArchUnit (Phase 9 / ADR 0016).
 */
@org.springframework.modulith.ApplicationModule(displayName = "AfterSales")
package com.fksoft.domain.aftersales;
