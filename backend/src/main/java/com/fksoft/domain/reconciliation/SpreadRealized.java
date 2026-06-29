package com.fksoft.domain.reconciliation;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a case's realized spread and FX gain/loss were computed (7.1). Consumer:
 * Intelligence (real margin, counterfactual).
 *
 * @param caseId the case id
 * @param realizedSpread the realized spread
 * @param fxGainLoss the FX gain (positive) or loss (negative)
 * @param occurredAt when it was realized
 */
public record SpreadRealized(
    UUID caseId, Money realizedSpread, Money fxGainLoss, Instant occurredAt) {}
