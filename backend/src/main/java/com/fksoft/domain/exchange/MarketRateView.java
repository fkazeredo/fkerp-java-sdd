package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a market-rate observation (SPEC-0011): the rate the market showed for a pair at
 * {@code observedAt}, with its origin. Carries the id as provenance.
 *
 * @param id the observation id
 * @param currencyPair the pair, e.g. {@code USD/BRL}
 * @param rate the market rate (scale 6, &gt; 0)
 * @param observedAt when the market showed this rate
 * @param source where the observation came from (FEED or MANUAL)
 */
public record MarketRateView(
    UUID id,
    CurrencyPair currencyPair,
    BigDecimal rate,
    Instant observedAt,
    MarketRateSource source) {}
