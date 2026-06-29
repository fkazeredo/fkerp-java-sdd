package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Business fact: a sell rate was pinned for a currency pair. Published in-process by the exchange
 * module; no consumer yet (future: Intelligence, Compliance/audit). Becomes a stable
 * contract/outbox once another module or service consumes it.
 *
 * @param currencyPair the pair, e.g. {@code USD/BRL}
 * @param rate the pinned rate
 * @param effectiveFrom when the rate starts to prevail
 * @param setBy who pinned it
 * @param occurredAt when the pinning happened
 */
public record RatePinned(
    CurrencyPair currencyPair,
    BigDecimal rate,
    Instant effectiveFrom,
    String setBy,
    Instant occurredAt) {}
