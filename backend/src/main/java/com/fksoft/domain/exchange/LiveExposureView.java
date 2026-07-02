package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.time.Instant;

/**
 * Read-model of the book's live FX exposure (SPEC-0011 BR6): the aggregate of all OPEN positions —
 * accrued subsidy plus current mark-to-market drift — with a drift alert raised when the absolute
 * drift exceeds the governed threshold (BR9/DL-0027: 2% of the open foreign exposure valued at the
 * freeze market rate). The alert never blocks. It is a projection: it never mutates positions.
 *
 * @param asOf the instant the exposure was evaluated
 * @param openPositions the number of OPEN positions in the book
 * @param accruedSubsidy the sum of the positions' subsidies (BRL)
 * @param markToMarketDrift the sum of the positions' current drifts (BRL)
 * @param totalExposure accruedSubsidy + markToMarketDrift (BRL)
 * @param driftThreshold the alert threshold in BRL (2% of the UNHEDGED exposure — SPEC-0032)
 * @param driftAlert whether |markToMarketDrift| exceeded the threshold (uncovered book only)
 * @param openForwards the number of OPEN forward contracts counting as coverage (SPEC-0032)
 * @param unhedgedExposureBase the BRL-at-freeze exposure NOT covered by forwards (threshold base)
 */
public record LiveExposureView(
    Instant asOf,
    long openPositions,
    Money accruedSubsidy,
    Money markToMarketDrift,
    Money totalExposure,
    Money driftThreshold,
    boolean driftAlert,
    long openForwards,
    Money unhedgedExposureBase) {}
