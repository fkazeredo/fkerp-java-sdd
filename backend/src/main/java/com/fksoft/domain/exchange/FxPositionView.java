package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of an {@code FxPosition} and its decomposition (SPEC-0011): the frozen provenance
 * (pinned rate, market at freeze), the subsidy accrued at opening, the current mark-to-market drift
 * (for an OPEN position, evaluated against a supplied "market now"), and — once CLOSED — the
 * realized drift and total gap.
 *
 * @param bookingId the booking that opened the position
 * @param foreignAmount the supplier cost in the foreign currency (the exposure leg)
 * @param pinnedRate the frozen sell rate (scale 6)
 * @param marketAtFreeze the market rate at the freeze instant (scale 6)
 * @param subsidy the subsidy accrued at opening (BRL; may be negative)
 * @param markToMarketDrift the current drift while OPEN (BRL; null once closed)
 * @param settlementRate the supplier settlement rate (scale 6; null while open)
 * @param realizedDrift the realized drift once closed (BRL; null while open)
 * @param totalGap the total gap once closed (BRL; null while open)
 * @param status OPEN or CLOSED
 * @param openedAt when the position opened
 */
public record FxPositionView(
    UUID bookingId,
    Money foreignAmount,
    BigDecimal pinnedRate,
    BigDecimal marketAtFreeze,
    Money subsidy,
    Money markToMarketDrift,
    BigDecimal settlementRate,
    Money realizedDrift,
    Money totalGap,
    FxPositionStatus status,
    Instant openedAt) {}
