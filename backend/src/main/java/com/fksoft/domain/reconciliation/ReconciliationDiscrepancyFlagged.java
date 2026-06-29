package com.fksoft.domain.reconciliation;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a case's realized spread diverged from the expected beyond tolerance (BR7).
 * Consumer: Intelligence (prioritized divergences).
 *
 * @param caseId the case id
 * @param expectedSpread the expected spread
 * @param realizedSpread the realized spread
 * @param delta the absolute difference that exceeded tolerance
 * @param occurredAt when it was flagged
 */
public record ReconciliationDiscrepancyFlagged(
    UUID caseId, Money expectedSpread, Money realizedSpread, Money delta, Instant occurredAt) {}
