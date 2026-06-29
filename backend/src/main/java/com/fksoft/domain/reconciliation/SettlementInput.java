package com.fksoft.domain.reconciliation;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;

/**
 * The realized values recorded against a case (BR3). Any subset may be provided (partial
 * settlement); when all are present the case becomes SETTLED. Money values must be in the case's
 * sale currency (BRL in v1); the settlement rate must be positive.
 *
 * @param amountReceivedFromAgency amount received from the agency (BRL)
 * @param supplierSettlementRate the rate the supplier was actually settled at (&gt; 0)
 * @param supplierPaidAmount amount paid to the supplier (BRL)
 * @param commissionReceivedFromSupplier commission received from the supplier (BRL)
 * @param commissionPaidToAgent commission paid to the agent (BRL)
 */
public record SettlementInput(
    Money amountReceivedFromAgency,
    BigDecimal supplierSettlementRate,
    Money supplierPaidAmount,
    Money commissionReceivedFromSupplier,
    Money commissionPaidToAgent) {}
