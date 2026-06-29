/**
 * Payout module (SPEC-0017): executes the financial outflows of the operation — the agent
 * commission repass, the supplier settlement (foreign currency, at the real settlement rate, with
 * the BRL baixa) and the customer refund — supporting installments, always with a receipt and a
 * trace (redesign Part 6/7). The aggregate is {@link com.fksoft.domain.payout.internal.Payout}:
 * kind ∈ {AGENT_COMMISSION, SUPPLIER_SETTLEMENT, REFUND}, a {@link com.fksoft.domain.payout.Payee},
 * the amount, the {@code settlementRate}/{@code settledBrl} for a foreign settlement (BR1,
 * DL-0049), the status machine PENDING→EXECUTING→EXECUTED|FAILED (BR2) and the installment plan
 * (BR6, DL-0050).
 *
 * <p>Spring Modulith application module — a <strong>leaf</strong>: it depends only on the {@code
 * money}/{@code error} kernels and its own {@link com.fksoft.domain.payout.PaymentGateway} port,
 * never on Finance or Compliance (so the module graph stays acyclic — Finance consumes the {@link
 * com.fksoft.domain.payout.SupplierSettled}/{@link com.fksoft.domain.payout.AgentCommissionPaid}/
 * {@link com.fksoft.domain.payout.RefundExecuted} events instead, like {@code finance → billing}).
 * Types in this base package are the module's public API: the {@link
 * com.fksoft.domain.payout.PayoutService} use cases, the {@link
 * com.fksoft.domain.payout.PaymentGateway} integration port (and its value objects), the commands/
 * views/value objects, the events and the business exceptions. The {@code internal} sub-package
 * (the aggregate, the installment entity and the repository) is module-private (Spring Modulith
 * verify).
 *
 * <p>The execution goes to the external world through an Anti-Corruption Layer: the {@link
 * com.fksoft.domain.payout.PaymentGateway} port and a <strong>traceable mock with an asynchronous
 * webhook</strong> in {@code com.fksoft.infra.integration.payment} (ADR 0006; DL-0048) — the
 * provider's shape never crosses into the domain (ArchUnit boundary test). Swapping to a real
 * provider is adding an adapter, not changing the domain. The cross-module orchestration of
 * execution (requesting the gateway, archiving the receipt in Compliance) lives in that infra
 * adapter.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payout")
package com.fksoft.domain.payout;
