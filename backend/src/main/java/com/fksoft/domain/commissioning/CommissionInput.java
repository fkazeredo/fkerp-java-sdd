package com.fksoft.domain.commissioning;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;

/**
 * Input to the two-sided commission calculation: a commissionable base and the two fixed
 * percentages (decimals in {@code [0,1]}). The caller passes the base already correct — base
 * exclusions per supplier are not computed here (BR4).
 *
 * @param commissionableBase the base the commissions are computed on
 * @param supplierCommissionPct the supplier override rate (to receive), in {@code [0,1]}
 * @param agentCommissionPct the agent commission rate (to pay), in {@code [0,1]}
 */
public record CommissionInput(
    Money commissionableBase, BigDecimal supplierCommissionPct, BigDecimal agentCommissionPct) {}
