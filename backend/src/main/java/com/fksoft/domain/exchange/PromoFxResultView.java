package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;

/**
 * Read-model of the FX promo result for a period (SPEC-0011, OVERVIEW 8.2-C): the sell gap of the
 * positions opened in the period, decomposed into <em>subsidy</em> (intentional promotion cost) and
 * <em>drift</em> (market risk), and their sum (<em>totalGap</em>). Drift uses the realized drift
 * for closed positions and the current mark-to-market drift for still-open ones. It is a
 * projection.
 *
 * @param period the period in {@code YYYY-MM}
 * @param positions the number of positions opened in the period
 * @param subsidy the sum of subsidies accrued in the period (BRL)
 * @param drift the sum of drifts (realized when closed, marked-to-market when open) (BRL)
 * @param totalGap subsidy + drift (BRL)
 */
public record PromoFxResultView(
    String period, long positions, Money subsidy, Money drift, Money totalGap) {}
