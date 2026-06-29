package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a supplier settlement was executed (SPEC-0017 BR5, Events). Published in-process
 * by the payout module when a {@code SUPPLIER_SETTLEMENT} payout finishes executing all its
 * installments. Consumed by <strong>Finance</strong> to post the supplier PAYABLE settlement
 * idempotently (DL-0051; like {@code finance → billing}/{@code finance → booking}). It carries the
 * settlement rate (so a downstream FX consumer could close the position) and {@code paidBrl} (the
 * BRL baixa Finance posts), so the consumer never reads back into Payout — keeping the module graph
 * acyclic (Payout is a leaf).
 *
 * @param payoutId the executed payout id (the idempotency source ref)
 * @param bookingId the related booking (value), or {@code null}
 * @param settlementRate the BRL settlement rate applied (scale 6), or {@code null} when BRL-native
 * @param paidBrl the BRL settled amount (the baixa Finance posts)
 * @param occurredAt when it was settled
 */
public record SupplierSettled(
    UUID payoutId,
    String bookingId,
    BigDecimal settlementRate,
    Money paidBrl,
    Instant occurredAt) {}
