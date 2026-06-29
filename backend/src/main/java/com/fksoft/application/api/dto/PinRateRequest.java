package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request body for {@code POST /api/exchange/pinned-rates}. The currency pair is parsed (and
 * validated) by the domain {@code CurrencyPair} value object; positivity of {@code rate} is a
 * domain invariant.
 *
 * @param currencyPair the pair as text, e.g. {@code USD/BRL} (required)
 * @param rate the sell rate (required, must be &gt; 0)
 * @param effectiveFrom optional instant the rate starts to prevail; defaults to now
 * @param note optional free note
 */
public record PinRateRequest(
    @NotBlank String currencyPair, @NotNull BigDecimal rate, Instant effectiveFrom, String note) {}
