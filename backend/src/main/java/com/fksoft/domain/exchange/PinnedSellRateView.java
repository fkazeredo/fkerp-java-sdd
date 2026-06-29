package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a pinned sell rate. Carries the rate's id so consumers (e.g. Quoting) can freeze it
 * as provenance, alongside the value and effective instant.
 *
 * @param id the pinned-rate id (provenance reference)
 * @param currencyPair the pair, e.g. {@code USD/BRL}
 * @param rate the pinned sell rate (scale 6, &gt; 0)
 * @param effectiveFrom the instant from which this rate prevails
 * @param setBy who pinned it
 * @param note optional free note
 */
public record PinnedSellRateView(
    UUID id,
    CurrencyPair currencyPair,
    BigDecimal rate,
    Instant effectiveFrom,
    String setBy,
    String note) {}
