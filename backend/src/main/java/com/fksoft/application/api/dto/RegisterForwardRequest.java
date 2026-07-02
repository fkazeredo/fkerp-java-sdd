package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for {@code POST /api/exchange/forwards} (SPEC-0032). */
public record RegisterForwardRequest(
    @NotBlank String currency,
    @NotNull @Positive BigDecimal notional,
    @NotNull @Positive BigDecimal contractRate,
    @NotNull LocalDate tradeDate,
    @NotNull LocalDate maturityDate,
    @NotBlank String counterparty) {}
