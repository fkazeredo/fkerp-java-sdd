package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for {@code POST /api/exchange/market-rates} (SPEC-0011): manual contingency
 * registration of a market-rate observation (DL-0025). The currency pair is parsed and validated by
 * the domain {@code CurrencyPair}; positivity of {@code rate} is a domain invariant. The source is
 * fixed to {@code MANUAL} by the delivery layer.
 *
 * @param currencyPair the pair as text, e.g. {@code USD/BRL} (required)
 * @param rate the market rate (required, must be &gt; 0)
 * @param observedAt optional instant the market showed this rate; defaults to now
 */
public record RecordMarketRateRequest(
    @NotBlank String currencyPair, @NotNull BigDecimal rate, Instant observedAt) {}
