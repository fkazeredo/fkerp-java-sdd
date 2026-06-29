package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/commissioning/preview}. The base is a {@code {amount,
 * currency}} money object; the percentages are decimals in {@code [0,1]} (range enforced by the
 * domain).
 *
 * @param commissionableBase the commissionable base (required)
 * @param supplierCommissionPct the supplier override rate (required)
 * @param agentCommissionPct the agent commission rate (required)
 */
public record CommissionPreviewRequest(
    @NotNull Money commissionableBase,
    @NotNull BigDecimal supplierCommissionPct,
    @NotNull BigDecimal agentCommissionPct) {}
