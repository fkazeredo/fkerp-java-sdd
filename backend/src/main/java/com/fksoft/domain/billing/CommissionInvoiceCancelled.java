package com.fksoft.domain.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a commission invoice (NFS-e) was cancelled (SPEC-0016 Events). Published
 * in-process by {@link BillingService}; frees the commission for a new invoice (the contabilistic
 * reversal in Finance is out of scope for SPEC-0016 — DL-0047).
 *
 * @param invoiceId the cancelled invoice id
 * @param reason the cancellation reason
 * @param occurredAt when it was cancelled
 */
public record CommissionInvoiceCancelled(UUID invoiceId, String reason, Instant occurredAt) {}
