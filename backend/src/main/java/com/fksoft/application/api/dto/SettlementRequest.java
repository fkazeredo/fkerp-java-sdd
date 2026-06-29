package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/reconciliation/{caseId}/settlement}. Any subset of the realized
 * legs may be provided (partial settlement); money values must be in the case's sale currency
 * (BRL).
 *
 * @param amountReceivedFromAgency amount received from the agency (BRL)
 * @param supplierSettlementRate the supplier settlement rate ({@code > 0})
 * @param supplierPaidAmount amount paid to the supplier (BRL)
 * @param commissionReceivedFromSupplier commission received from the supplier (BRL)
 * @param commissionPaidToAgent commission paid to the agent (BRL)
 */
public record SettlementRequest(
    Money amountReceivedFromAgency,
    @Positive BigDecimal supplierSettlementRate,
    Money supplierPaidAmount,
    Money commissionReceivedFromSupplier,
    Money commissionPaidToAgent) {}
