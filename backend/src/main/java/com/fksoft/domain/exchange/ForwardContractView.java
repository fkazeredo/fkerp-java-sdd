package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read view of an FX forward contract (SPEC-0032, DL-0130).
 *
 * @param settlementResultBrl {@code (settledRate − contractRate) × notional} (BRL, scale 2); null
 *     until settled
 */
public record ForwardContractView(
    UUID id,
    String currency,
    BigDecimal notional,
    BigDecimal contractRate,
    LocalDate tradeDate,
    LocalDate maturityDate,
    String counterparty,
    ForwardStatus status,
    BigDecimal settledRate,
    BigDecimal settlementResultBrl,
    Instant settledAt,
    Instant cancelledAt,
    Instant createdAt) {}
