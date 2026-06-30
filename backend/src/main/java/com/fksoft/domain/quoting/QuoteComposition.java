package com.fksoft.domain.quoting;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal parameter object holding the fully-computed composition the service freezes into a
 * {@link Quote} (BR4). Bundling the ~16 frozen values keeps the {@code Quote.compose} factory
 * readable.
 *
 * @param priceOrigin price origin (MANUAL)
 * @param basePrice base price in supplier currency
 * @param currencyPair the pair used
 * @param fxRate the frozen rate value
 * @param rateId the frozen rate id (provenance)
 * @param baseConverted base converted to sale currency
 * @param supplierPct supplier commission rate
 * @param agentPct agent commission rate
 * @param supplierCommission frozen supplier commission
 * @param agentCommission frozen agent commission
 * @param spread frozen spread
 * @param spreadNegative whether the spread is negative
 * @param markupPct markup rate
 * @param markupAmount markup amount
 * @param markupSource markup governance source
 * @param suggestedAmount suggested sale price
 */
@ModuleInternal
public record QuoteComposition(
    PriceOrigin priceOrigin,
    Money basePrice,
    CurrencyPair currencyPair,
    BigDecimal fxRate,
    UUID rateId,
    Money baseConverted,
    BigDecimal supplierPct,
    BigDecimal agentPct,
    Money supplierCommission,
    Money agentCommission,
    Money spread,
    boolean spreadNegative,
    BigDecimal markupPct,
    Money markupAmount,
    String markupSource,
    Money suggestedAmount) {}
