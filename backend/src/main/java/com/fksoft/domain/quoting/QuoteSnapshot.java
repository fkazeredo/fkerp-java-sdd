package com.fksoft.domain.quoting;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Frozen snapshot of a quote exposed to other modules through {@link QuoteDirectory}: the account
 * it belongs to (used by Booking, SPEC-0006) and the frozen financials (used by Reconciliation,
 * SPEC-0007, to open a case with the expected commissions and spread). Carries no entity.
 *
 * @param quoteId the quote id
 * @param accountId the account the quote belongs to
 * @param basePrice the external base price (supplier currency)
 * @param pinnedRate the frozen sell rate used
 * @param baseConverted the base converted to the sale currency
 * @param expectedSupplierCommission the frozen supplier commission
 * @param expectedAgentCommission the frozen agent commission
 * @param expectedSpread the frozen expected spread
 */
public record QuoteSnapshot(
    UUID quoteId,
    UUID accountId,
    Money basePrice,
    BigDecimal pinnedRate,
    Money baseConverted,
    Money expectedSupplierCommission,
    Money expectedAgentCommission,
    Money expectedSpread) {}
