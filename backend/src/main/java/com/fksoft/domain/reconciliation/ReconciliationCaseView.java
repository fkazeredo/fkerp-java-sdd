package com.fksoft.domain.reconciliation;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read view of a reconciliation case: the frozen expected values, the realized derivations (when
 * settled) and the current status. Money in the sale currency (BRL in v1).
 *
 * @param caseId case id
 * @param bookingId the booking that opened it
 * @param baseAmount the base in the foreign (supplier) currency
 * @param pinnedRate the frozen sell rate
 * @param baseBrl the base converted to the sale currency
 * @param expectedSupplierCommission expected supplier commission
 * @param expectedAgentCommission expected agent commission
 * @param expectedSpread expected spread
 * @param realizedSpread realized spread (null until settled)
 * @param fxGainLoss FX gain/loss (null until the settlement rate is recorded)
 * @param discrepancy absolute |realized − expected| (zero until computed)
 * @param status case status
 */
public record ReconciliationCaseView(
    UUID caseId,
    UUID bookingId,
    Money baseAmount,
    BigDecimal pinnedRate,
    Money baseBrl,
    Money expectedSupplierCommission,
    Money expectedAgentCommission,
    Money expectedSpread,
    Money realizedSpread,
    Money fxGainLoss,
    Money discrepancy,
    CaseStatus status) {}
