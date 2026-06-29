package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: under ALL_SALES_FINAL the seller incurred a supplier/marketplace obligation that
 * is <strong>due even when the customer is refunded</strong> — the merchant trap (SPEC-0010 Events,
 * OVERVIEW 8.2-G/H). Published precisely to make that non-netting obligation <em>visible</em>
 * rather than letting it disappear in a net amount. Published in-process; future consumer is
 * Intelligence (open merchant exposure).
 *
 * @param bookingId the cancelled booking id
 * @param supplierCharge the irrecoverable supplier obligation
 * @param occurredAt when it was incurred
 */
public record MerchantObligationIncurred(
    UUID bookingId, Charge supplierCharge, Instant occurredAt) {}
