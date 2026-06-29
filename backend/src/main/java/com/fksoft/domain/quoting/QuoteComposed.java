package com.fksoft.domain.quoting;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a quote was composed. Published in-process; future consumers include Intelligence
 * and AfterSales.
 *
 * @param quoteId the composed quote id
 * @param accountId the account it belongs to
 * @param suggestedAmount the system-suggested sale price
 * @param occurredAt when composition happened
 */
public record QuoteComposed(
    UUID quoteId, UUID accountId, Money suggestedAmount, Instant occurredAt) {}
