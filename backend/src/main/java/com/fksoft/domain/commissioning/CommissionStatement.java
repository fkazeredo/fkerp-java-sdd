package com.fksoft.domain.commissioning;

import com.fksoft.domain.money.Money;

/**
 * Result of the two-sided commission calculation (BR1): the supplier commission to receive, the
 * agent commission to pay, the derived spread, and whether the spread is negative (BR3 — exposed,
 * never hidden; Commissioning does not block).
 *
 * @param supplierCommission commission to receive from the supplier
 * @param agentCommission commission to pay to the agent
 * @param spread {@code supplierCommission - agentCommission}
 * @param spreadNegative {@code true} when the agent rate exceeds the supplier rate
 */
public record CommissionStatement(
    Money supplierCommission, Money agentCommission, Money spread, boolean spreadNegative) {}
