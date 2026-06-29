package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request body for {@code POST /api/quotes}. The currency pair is parsed by the domain {@code
 * CurrencyPair}; percentage range and provenance are domain concerns.
 *
 * @param accountId the account the quote is for (required)
 * @param basePrice the external base price {@code {amount, currency}} (required)
 * @param currencyPair the pair to convert with, e.g. {@code USD/BRL} (required)
 * @param supplierCommissionPct the supplier override rate (required)
 * @param agentCommissionPct the agent commission rate (required)
 * @param validUntil optional validity instant
 */
public record ComposeQuoteRequest(
    @NotNull UUID accountId,
    @NotNull Money basePrice,
    @NotBlank String currencyPair,
    @NotNull BigDecimal supplierCommissionPct,
    @NotNull BigDecimal agentCommissionPct,
    Instant validUntil) {}
