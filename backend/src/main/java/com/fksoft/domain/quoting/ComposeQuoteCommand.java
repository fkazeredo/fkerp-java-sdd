package com.fksoft.domain.quoting;

import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Command to compose a MANUAL quote. The API shape differs from the use-case input (the controller
 * parses the currency pair and resolves the actor), so a command record is justified here.
 *
 * @param accountId the account the quote is for
 * @param basePrice the external/manual base price (supplier currency, e.g. USD)
 * @param currencyPair the pair to convert with (e.g. USD/BRL)
 * @param supplierCommissionPct the supplier override rate
 * @param agentCommissionPct the agent commission rate
 * @param validUntil optional validity instant
 */
public record ComposeQuoteCommand(
    UUID accountId,
    Money basePrice,
    CurrencyPair currencyPair,
    BigDecimal supplierCommissionPct,
    BigDecimal agentCommissionPct,
    Instant validUntil) {}
